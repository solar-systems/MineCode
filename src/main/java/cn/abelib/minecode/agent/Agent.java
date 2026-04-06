package cn.abelib.minecode.agent;

import cn.abelib.minecode.context.ContextManager;
import cn.abelib.minecode.hook.*;
import cn.abelib.minecode.llm.LLMClient;
import cn.abelib.minecode.llm.LLMResponse;
import cn.abelib.minecode.llm.ToolCall;
import cn.abelib.minecode.plan.PlanNotebook;
import cn.abelib.minecode.plan.Task;
import cn.abelib.minecode.plan.PlanHook;
import cn.abelib.minecode.prompt.SystemPrompt;
import cn.abelib.minecode.tools.Tool;
import cn.abelib.minecode.tools.ToolGroupManager;
import cn.abelib.minecode.tools.ToolPreset;
import cn.abelib.minecode.tools.ToolRegistry;
import cn.abelib.minecode.tools.impl.AgentTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 核心代理类 - Agent 循环的实现
 *
 * <p>这是 MineCode 的核心。设计模式：
 * <pre>
 * 用户消息 -> LLM (带工具) -> 工具调用? -> 执行 -> 循环
 *                       -> 文本响应? -> 返回用户
 * </pre>
 *
 * <p>持续循环直到 LLM 返回纯文本（无工具调用），
 * 表示它已完成工作并准备向用户报告。
 *
 * <p>关键设计：
 * <ul>
 *   <li>并行工具执行 - 当 LLM 返回多个工具调用时并行执行</li>
 *   <li>上下文压缩 - 当上下文过长时自动压缩</li>
 *   <li>Hook 系统 - 支持事件拦截和扩展</li>
 *   <li>Builder 模式 - 灵活的配置方式</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * Agent agent = Agent.builder()
 *     .llm(llmClient)
 *     .name("CodeAssistant")
 *     .hook(new LoggingHook())
 *     .hook(new TokenStatsHook())
 *     .maxRounds(30)
 *     .build();
 *
 * String response = agent.chat("Hello!");
 * }</pre>
 *
 * @author Abel
 */
public class Agent {
    private static final Logger log = LoggerFactory.getLogger(Agent.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    // 核心组件
    private final LLMClient llm;
    private final ToolGroupManager toolGroupManager;
    private final ContextManager contextManager;
    private final HookManager hookManager;
    private ObjectNode systemPrompt;

    // 配置参数
    private final String name;
    private final String description;
    private final int maxRounds;

    // 运行时状态
    private final List<ObjectNode> messages;
    private final ExecutorService executor;

    // 任务规划（可选）
    private PlanNotebook planNotebook;

    // 中断控制
    private final InterruptContext interruptContext;

    /**
     * 私有构造函数，使用 Builder 创建
     */
    private Agent(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.llm = builder.llm;
        this.toolGroupManager = new ToolGroupManager();
        this.maxRounds = builder.maxRounds;
        this.contextManager = new ContextManager(builder.maxContextTokens);
        this.hookManager = new HookManager();
        this.messages = new ArrayList<>();
        this.executor = Executors.newFixedThreadPool(8);
        this.interruptContext = new InterruptContext();

        // 注册 Hooks
        if (builder.hooks != null) {
            hookManager.registerAll(builder.hooks);
        }

        // 初始化任务规划（如果提供）
        if (builder.planNotebook != null) {
            this.planNotebook = builder.planNotebook;
            // 自动添加 PlanHook 注入计划上下文
            hookManager.register(new PlanHook(planNotebook));
        }

        // 应用工具预设（如果提供）
        if (builder.toolPreset != null) {
            toolGroupManager.applyPreset(builder.toolPreset);
        }

        // 自定义工具列表（如果提供）
        if (builder.tools != null) {
            // 注册自定义工具组
            toolGroupManager.registerGroup(new cn.abelib.minecode.tools.ToolGroup(
                    "custom", "自定义工具", true, builder.tools.toArray(new Tool[0])));
        }

        // 生成系统提示（基于激活的工具）
        updateSystemPrompt();

        // 初始化 AgentTool
        initializeAgentTools(builder.maxContextTokens);
    }

    /**
     * 创建 Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 处理用户消息
     *
     * @param userInput 用户输入
     * @return 最终响应
     */
    public String chat(String userInput) throws IOException {
        return chat(userInput, null);
    }

    /**
     * 处理用户消息（带简化回调）
     *
     * @param userInput 用户输入
     * @param callback  回调接口（可选）
     * @return 最终响应
     */
    public String chat(String userInput, ChatCallback callback) throws IOException {
        // 重置中断状态并标记执行开始
        interruptContext.reset();
        interruptContext.markExecutionStart();

        // 触发 PreCall 事件
        PreCallEvent preCall = new PreCallEvent(this, userInput);
        HookEvent result = hookManager.trigger(preCall);
        if (result == null) {
            return handleInterrupted("PreCall Hook 中断");
        }
        userInput = ((PreCallEvent) result).getUserInput();

        // 添加用户消息
        ObjectNode userMsg = mapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", userInput);
        messages.add(userMsg);

        // 可能需要压缩
        contextManager.maybeCompress(messages, llm);

        int roundsUsed = 0;
        String finalResponse = null;

        for (int round = 0; round < maxRounds; round++) {
            // 检查中断
            if (interruptContext.isInterrupted()) {
                finalResponse = handleInterrupted(interruptContext.getMessage());
                break;
            }

            roundsUsed = round + 1;

            // 构建完整消息列表（系统提示 + 历史）
            List<ObjectNode> fullMessages = buildFullMessages();

            // 触发 PreReasoning 事件
            PreReasoningEvent preReasoning = new PreReasoningEvent(this, fullMessages);
            result = hookManager.trigger(preReasoning);
            if (result == null) {
                finalResponse = handleInterrupted("PreReasoning Hook 中断");
                break;
            }
            fullMessages = ((PreReasoningEvent) result).getMessages();

            // 获取工具 Schema
            List<ObjectNode> toolSchemas = getToolSchemas();

            // 调用 LLM
            LLMResponse response;
            try {
                response = llm.chat(fullMessages, toolSchemas, token -> {
                    // 触发流式 Token 事件
                    ReasoningChunkEvent chunk = new ReasoningChunkEvent(this, token, false);
                    hookManager.triggerSimple(chunk);

                    // 同时调用简化回调
                    if (callback != null) {
                        callback.onToken(token);
                    }
                });
            } catch (Exception e) {
                // 触发错误事件
                ErrorEvent error = new ErrorEvent(this, e, "reasoning");
                hookManager.triggerSimple(error);
                throw new IOException("LLM 调用失败", e);
            }

            // 触发 PostReasoning 事件
            PostReasoningEvent postReasoning = new PostReasoningEvent(
                    this, response.content(), response.toolCalls(),
                    response.promptTokens(), response.completionTokens()
            );
            HookEvent postResult = hookManager.trigger(postReasoning);

            // 检查 gotoReasoning 请求（控制流跳转）
            if (postResult instanceof PostReasoningEvent e && e.isGotoReasoningRequested()) {
                log.info("Hook requested gotoReasoning - jumping back to reasoning phase");
                // 跳回推理阶段
                messages.clear();
                messages.addAll(e.getGotoReasoningMessages());
                continue;  // 重新进入推理循环
            }

            // 无工具调用 -> LLM 完成，返回文本
            if (!response.hasToolCalls()) {
                messages.add(response.toMessage());
                finalResponse = response.content();
                break;
            }

            // 有工具调用 -> 执行
            messages.add(response.toMessage());

            if (response.toolCalls().size() == 1) {
                // 单个工具调用
                ToolCall tc = response.toolCalls().get(0);
                executeToolWithHooks(tc, callback);
            } else {
                // 并行执行多个工具调用
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (ToolCall tc : response.toolCalls()) {
                    futures.add(CompletableFuture.runAsync(() -> {
                        executeToolWithHooks(tc, callback);
                    }, executor));
                }

                // 等待所有完成
                for (CompletableFuture<Void> future : futures) {
                    try {
                        future.get();
                    } catch (Exception e) {
                        log.error("Tool execution failed", e);
                    }
                }
            }

            // 工具输出可能很大，检查是否需要压缩
            contextManager.maybeCompress(messages, llm);
        }

        if (finalResponse == null) {
            finalResponse = "(达到最大工具调用轮数)";
        }

        // 触发 PostCall 事件
        PostCallEvent postCall = new PostCallEvent(this, finalResponse, roundsUsed);
        hookManager.triggerSimple(postCall);

        return finalResponse;
    }

    /**
     * 处理中断情况
     */
    private String handleInterrupted(String defaultMessage) {
        InterruptReason reason = interruptContext.getReason();
        String message = interruptContext.getMessage();
        long executionTime = interruptContext.getExecutionTimeMs();

        // 触发 InterruptedEvent
        InterruptedEvent interruptedEvent = new InterruptedEvent(
                this, message, reason, interruptContext.getCause(), executionTime);
        hookManager.triggerSimple(interruptedEvent);

        return String.format("(中断: %s - %s)", reason.getDisplayName(), message != null ? message : defaultMessage);
    }

    /**
     * 执行工具（带 Hook 拦截）
     */
    private void executeToolWithHooks(ToolCall tc, ChatCallback callback) {
        // 触发 PreActing 事件
        PreActingEvent preActing = new PreActingEvent(this, tc);
        HookEvent result = hookManager.trigger(preActing);
        if (result == null) {
            addToolResult(tc, "(工具执行被 Hook 中断)");
            return;
        }
        tc = ((PreActingEvent) result).getToolCall();

        // 执行工具
        long startTime = System.currentTimeMillis();
        String toolResult;
        boolean success = true;

        try {
            Tool tool = ToolRegistry.getTool(tc.name());
            if (tool == null) {
                toolResult = "Error: 未知工具 '" + tc.name() + "'";
                success = false;
            } else {
                toolResult = tool.execute(tc.arguments());
                if (toolResult != null && toolResult.startsWith("Error")) {
                    success = false;
                }
            }
        } catch (Exception e) {
            toolResult = "Error executing " + tc.name() + ": " + e.getMessage();
            success = false;

            // 触发错误事件
            ErrorEvent error = new ErrorEvent(this, e, "acting:" + tc.name());
            hookManager.triggerSimple(error);
        }

        long durationMs = System.currentTimeMillis() - startTime;

        // 触发 PostActing 事件
        PostActingEvent postActing = new PostActingEvent(this, tc, toolResult, durationMs, success);
        hookManager.triggerSimple(postActing);

        // 调用简化回调
        if (callback != null) {
            callback.onTool(tc.name(), tc.arguments(), toolResult);
        }

        addToolResult(tc, toolResult);
    }

    /**
     * 添加工具执行结果到消息列表
     */
    private void addToolResult(ToolCall tc, String result) {
        ObjectNode msg = mapper.createObjectNode();
        msg.put("role", "tool");
        msg.put("tool_call_id", tc.id());
        msg.put("content", result);
        messages.add(msg);
    }

    /**
     * 构建完整消息列表
     */
    private List<ObjectNode> buildFullMessages() {
        List<ObjectNode> full = new ArrayList<>();
        full.add(systemPrompt);
        full.addAll(messages);
        return full;
    }

    /**
     * 获取工具 Schema 列表（基于激活的工具）
     */
    private List<ObjectNode> getToolSchemas() {
        return toolGroupManager.getActiveToolSchemas();
    }

    /**
     * 获取激活的工具列表
     */
    public List<Tool> getActiveTools() {
        return toolGroupManager.getActiveTools();
    }

    /**
     * 更新系统提示（基于当前激活的工具）
     */
    private void updateSystemPrompt() {
        this.systemPrompt = SystemPrompt.generate(getActiveTools());
    }

    /**
     * 初始化 AgentTool（如果存在）
     */
    private void initializeAgentTools(int maxContextTokens) {
        List<Tool> activeTools = getActiveTools();
        for (Tool tool : activeTools) {
            if (tool instanceof AgentTool agentTool) {
                agentTool.initialize(llm, activeTools, maxContextTokens);
            }
        }
    }

    // ==================== Getter 方法 ====================

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<Tool> getTools() {
        return toolGroupManager.getActiveTools();
    }

    /**
     * 获取工具组管理器
     */
    public ToolGroupManager getToolGroupManager() {
        return toolGroupManager;
    }

    public HookManager getHookManager() {
        return hookManager;
    }

    public List<ObjectNode> getMessages() {
        return messages;
    }

    public int getMaxRounds() {
        return maxRounds;
    }

    public PlanNotebook getPlanNotebook() {
        return planNotebook;
    }

    // ==================== 中断控制 ====================

    /**
     * 获取中断上下文
     */
    public InterruptContext getInterruptContext() {
        return interruptContext;
    }

    /**
     * 中断 Agent 执行
     *
     * @param message 中断消息
     */
    public void interrupt(String message) {
        interruptContext.interrupt(message);
    }

    /**
     * 中断 Agent 执行（带原因枚举）
     *
     * @param message 中断消息
     * @param reason  中断原因
     */
    public void interrupt(String message, InterruptReason reason) {
        interruptContext.interrupt(message, reason);
    }

    /**
     * 中断 Agent 执行（带原因）
     *
     * @param message 中断消息
     * @param cause   中断异常
     */
    public void interrupt(String message, Throwable cause) {
        interruptContext.interrupt(message, cause);
    }

    /**
     * 中断 Agent 执行（完整参数）
     *
     * @param message 中断消息
     * @param reason  中断原因枚举
     * @param cause   中断异常（可为 null）
     */
    public void interrupt(String message, InterruptReason reason, Throwable cause) {
        interruptContext.interrupt(message, reason, cause);
    }

    /**
     * 是否已被中断
     */
    public boolean isInterrupted() {
        return interruptContext.isInterrupted();
    }

    /**
     * 获取中断消息
     */
    public String getInterruptMessage() {
        return interruptContext.getMessage();
    }

    /**
     * 获取中断原因
     */
    public InterruptReason getInterruptReason() {
        return interruptContext.getReason();
    }

    /**
     * 是否可恢复中断
     */
    public boolean isInterruptRecoverable() {
        return interruptContext.isRecoverable();
    }

    /**
     * 清除中断状态
     */
    public void clearInterrupt() {
        interruptContext.reset();
    }

    // ==================== 任务规划模式 ====================

    /**
     * 使用任务规划模式执行复杂任务
     *
     * <p>自动将目标分解为子任务，逐个执行并跟踪进度。
     *
     * <p>使用示例：
     * <pre>{@code
     * Agent agent = Agent.builder()
     *     .llm(llm)
     *     .build();
     *
     * String result = agent.chatWithPlan("重构用户认证模块，提高安全性");
     * }</pre>
     *
     * @param goal 任务目标
     * @return 执行结果摘要
     */
    public String chatWithPlan(String goal) throws IOException {
        return chatWithPlan(goal, null);
    }

    /**
     * 使用任务规划模式执行复杂任务（带回调）
     *
     * @param goal     任务目标
     * @param callback 进度回调
     * @return 执行结果摘要
     */
    public String chatWithPlan(String goal, PlanCallback callback) throws IOException {
        // 创建或复用 PlanNotebook
        if (planNotebook == null) {
            planNotebook = new PlanNotebook();
        }

        // 检查是否已注册 PlanHook，避免重复注册
        boolean hasPlanHook = hookManager.getHooks().stream()
                .anyMatch(h -> h instanceof PlanHook);
        if (!hasPlanHook) {
            hookManager.register(new PlanHook(planNotebook));
        }

        // LLM 分解任务
        planNotebook.createPlanWithLLM(goal, llm);

        if (callback != null) {
            callback.onPlanCreated(planNotebook.getAllTasks());
        }

        log.info("Plan created with {} tasks for goal: {}", planNotebook.getAllTasks().size(), goal);

        // 逐个执行任务
        int completedCount = 0;
        int failedCount = 0;
        StringBuilder results = new StringBuilder();

        while (planNotebook.hasNextTask()) {
            Task task = planNotebook.getNextPendingTask().get();
            planNotebook.markInProgress(task.getId());

            if (callback != null) {
                callback.onTaskStarted(task);
            }

            try {
                // 构建带计划上下文的 prompt
                String prompt = buildTaskPrompt(task);

                // 执行任务
                String result = chat(prompt, callback);

                planNotebook.markCompleted(task.getId(), result);
                completedCount++;

                results.append(String.format("\n### %s: %s\n%s\n",
                        task.getId(), task.getDescription(),
                        result.length() > 500 ? result.substring(0, 500) + "..." : result));

                if (callback != null) {
                    callback.onTaskCompleted(task, result);
                }

            } catch (Exception e) {
                planNotebook.markFailed(task.getId(), e.getMessage());
                failedCount++;

                if (callback != null) {
                    callback.onTaskFailed(task, e);
                }

                log.error("Task {} failed: {}", task.getId(), e.getMessage());
            }
        }

        // 生成结果摘要
        String summary = String.format("""
                任务执行完成！

                目标：%s
                总任务数：%d
                成功：%d
                失败：%d

                %s
                """,
                goal,
                planNotebook.getAllTasks().size(),
                completedCount,
                failedCount,
                planNotebook.exportToText()
        );

        if (callback != null) {
            callback.onPlanCompleted(summary, completedCount, failedCount);
        }

        return summary;
    }

    /**
     * 构建任务执行的 prompt
     */
    private String buildTaskPrompt(Task task) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("请执行以下任务：\n\n");
        prompt.append("【当前任务】\n");
        prompt.append(task.getDescription()).append("\n\n");

        // 添加计划上下文
        prompt.append("【整体计划进度】\n");
        prompt.append(planNotebook.exportToText()).append("\n");

        // 添加前置任务结果
        if (task.getDependencies() != null && !task.getDependencies().isEmpty()) {
            prompt.append("【前置任务结果】\n");
            for (String depId : task.getDependencies()) {
                Task dep = planNotebook.getTask(depId);
                if (dep != null && dep.isCompleted()) {
                    prompt.append("- ").append(depId).append(": ");
                    String depResult = dep.getResult();
                    if (depResult != null && depResult.length() > 200) {
                        prompt.append(depResult.substring(0, 200)).append("...\n");
                    } else {
                        prompt.append(depResult).append("\n");
                    }
                }
            }
            prompt.append("\n");
        }

        prompt.append("请开始执行当前任务，完成后报告结果。");

        return prompt.toString();
    }

    /**
     * 设置 PlanNotebook（手动创建计划）
     */
    public void setPlanNotebook(PlanNotebook planNotebook) {
        this.planNotebook = planNotebook;

        // 检查是否已注册 PlanHook，避免重复注册
        boolean hasPlanHook = hookManager.getHooks().stream()
                .anyMatch(h -> h instanceof PlanHook);
        if (!hasPlanHook) {
            hookManager.register(new PlanHook(planNotebook));
        }
    }

    /**
     * 清空当前计划
     */
    public void clearPlan() {
        if (planNotebook != null) {
            planNotebook.clear();
        }
    }

    // ==================== 状态管理 ====================

    /**
     * 重置对话历史
     */
    public void reset() {
        messages.clear();
    }

    /**
     * 添加 Hook
     */
    public void addHook(Hook hook) {
        hookManager.register(hook);
    }

    /**
     * 移除 Hook
     */
    public void removeHook(Hook hook) {
        hookManager.remove(hook);
    }

    // ==================== 工具组管理 ====================

    /**
     * 激活工具组
     *
     * @param groupName 组名
     */
    public void activateToolGroup(String groupName) {
        toolGroupManager.activateGroup(groupName);
        updateSystemPrompt();
        log.info("Tool group activated: {}", groupName);
    }

    /**
     * 禁用工具组
     *
     * @param groupName 组名
     */
    public void deactivateToolGroup(String groupName) {
        toolGroupManager.deactivateGroup(groupName);
        updateSystemPrompt();
        log.info("Tool group deactivated: {}", groupName);
    }

    /**
     * 切换工具组状态
     *
     * @param groupName 组名
     */
    public void toggleToolGroup(String groupName) {
        toolGroupManager.toggleGroup(groupName);
        updateSystemPrompt();
    }

    /**
     * 应用工具预设
     *
     * @param preset 预设模式
     */
    public void applyToolPreset(ToolPreset preset) {
        toolGroupManager.applyPreset(preset);
        updateSystemPrompt();
        initializeAgentTools(contextManager.getMaxTokens());
        log.info("Applied tool preset: {}", preset.name());
    }

    /**
     * 获取工具组状态
     *
     * @return 组名 -> 是否激活
     */
    public java.util.Map<String, Boolean> getToolGroupStatus() {
        return toolGroupManager.getGroupStatus();
    }

    /**
     * 关闭资源
     */
    public void close() {
        executor.shutdown();
    }

    // ==================== 简化回调接口 ====================

    /**
     * 简化的聊天回调接口
     */
    @FunctionalInterface
    public interface ChatCallback {
        void onToken(String token);

        default void onTool(String name, JsonNode arguments, String result) {
            // 默认空实现
        }
    }

    /**
     * 任务规划回调接口
     */
    public interface PlanCallback extends ChatCallback {
        /**
         * 计划创建完成
         */
        default void onPlanCreated(List<Task> tasks) {}

        /**
         * 任务开始执行
         */
        default void onTaskStarted(Task task) {}

        /**
         * 任务执行完成
         */
        default void onTaskCompleted(Task task, String result) {}

        /**
         * 任务执行失败
         */
        default void onTaskFailed(Task task, Exception error) {}

        /**
         * 整个计划执行完成
         */
        default void onPlanCompleted(String summary, int completed, int failed) {}

        @Override
        default void onToken(String token) {
            // 默认空实现
        }
    }

    // ==================== Builder ====================

    /**
     * Agent Builder
     *
     * <p>使用示例：
     * <pre>{@code
     * Agent agent = Agent.builder()
     *     .llm(llmClient)
     *     .name("CodeAssistant")
     *     .description("帮助编写代码的 AI 助手")
     *     .hook(new LoggingHook())
     *     .hook(new TokenStatsHook())
     *     .maxContextTokens(128000)
     *     .maxRounds(50)
     *     .build();
     * }</pre>
     */
    public static class Builder {
        // 必需参数
        private LLMClient llm;

        // 可选参数（带默认值）
        private String name = "MineCode Agent";
        private String description = "AI coding assistant";
        private List<Tool> tools;
        private List<Hook> hooks;
        private ToolPreset toolPreset;
        private int maxContextTokens = 128_000;
        private int maxRounds = 50;
        private PlanNotebook planNotebook;

        /**
         * 设置 LLM 客户端（必需）
         */
        public Builder llm(LLMClient llm) {
            this.llm = llm;
            return this;
        }

        /**
         * 设置 Agent 名称
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * 设置 Agent 描述
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * 设置工具列表
         */
        public Builder tools(List<Tool> tools) {
            this.tools = tools;
            return this;
        }

        /**
         * 添加单个工具
         */
        public Builder tool(Tool tool) {
            if (this.tools == null) {
                this.tools = new ArrayList<>();
            }
            this.tools.add(tool);
            return this;
        }

        /**
         * 设置 Hook 列表
         */
        public Builder hooks(List<Hook> hooks) {
            this.hooks = hooks;
            return this;
        }

        /**
         * 添加单个 Hook
         */
        public Builder hook(Hook hook) {
            if (this.hooks == null) {
                this.hooks = new ArrayList<>();
            }
            this.hooks.add(hook);
            return this;
        }

        /**
         * 设置工具预设模式
         *
         * <p>预设模式包括：
         * <ul>
         *   <li>READ_ONLY - 只读模式：文件读取 + 搜索</li>
         *   <li>STANDARD - 标准模式：文件操作 + 搜索 + 子代理</li>
         *   <li>SAFE_EDIT - 安全编辑：文件操作 + 搜索（无命令执行）</li>
         *   <li>FULL_ACCESS - 完全访问：所有工具</li>
         *   <li>MINIMAL - 最小模式：只有搜索工具</li>
         *   <li>SEARCH - 搜索模式：搜索 + 子代理</li>
         * </ul>
         *
         * @param preset 工具预设
         */
        public Builder toolPreset(ToolPreset preset) {
            this.toolPreset = preset;
            return this;
        }

        /**
         * 设置最大上下文 Token 数
         */
        public Builder maxContextTokens(int maxContextTokens) {
            this.maxContextTokens = maxContextTokens;
            return this;
        }

        /**
         * 设置最大工具调用轮数
         */
        public Builder maxRounds(int maxRounds) {
            this.maxRounds = maxRounds;
            return this;
        }

        /**
         * 设置任务规划笔记本
         */
        public Builder planNotebook(PlanNotebook planNotebook) {
            this.planNotebook = planNotebook;
            return this;
        }

        /**
         * 构建 Agent 实例
         *
         * @throws IllegalStateException 如果未设置必需的 LLM 客户端
         */
        public Agent build() {
            if (llm == null) {
                throw new IllegalStateException("LLMClient is required");
            }
            return new Agent(this);
        }
    }
}

package cn.abelib.minecode.agent;

import cn.abelib.minecode.llm.LLMClient;
import cn.abelib.minecode.llm.LLMResponse;
import cn.abelib.minecode.llm.ToolCall;
import cn.abelib.minecode.tools.Tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 核心 Agent 类 - ReAct 循环的最简实现
 *
 * <p>设计模式（来自 Claude Code）：
 * <pre>
 * 用户消息 -> LLM (带工具) -> 工具调用? -> 执行 -> 循环
 *                       -> 文本响应? -> 返回用户
 * </pre>
 *
 * <p>持续循环直到 LLM 返回纯文本（无工具调用），
 * 表示它已完成工作并准备向用户报告。
 *
 * <p>使用示例：
 * <pre>{@code
 * Agent agent = Agent.builder()
 *     .llm(llmClient)
 *     .maxRounds(30)
 *     .build();
 *
 * String response = agent.chat("帮我写一个 Hello World 程序");
 * }</pre>
 *
 * @author Abel
 */
public class Agent {
    private static final Logger log = LoggerFactory.getLogger(Agent.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    // 核心组件
    private final LLMClient llm;
    private final Map<String, Tool> tools = new HashMap<>();

    // 配置参数
    private final int maxRounds;

    // 对话历史
    private final List<ObjectNode> messages = new ArrayList<>();

    // 系统提示
    private final ObjectNode systemPrompt;

    /**
     * 私有构造函数，使用 Builder 创建
     */
    private Agent(Builder builder) {
        this.llm = builder.llm;
        this.maxRounds = builder.maxRounds;

        // 注册工具
        for (Tool tool : builder.tools) {
            this.tools.put(tool.getName(), tool);
        }

        // 生成系统提示
        this.systemPrompt = buildSystemPrompt();
    }

    /**
     * 创建 Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 处理用户消息 - 核心 ReAct 循环
     *
     * @param userInput 用户输入
     * @return 最终响应
     */
    public String chat(String userInput) throws IOException {
        return chat(userInput, null);
    }

    /**
     * 处理用户消息（带流式回调）
     */
    public String chat(String userInput, Consumer<String> onToken) throws IOException {
        // 添加用户消息
        ObjectNode userMsg = mapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", userInput);
        messages.add(userMsg);

        String finalResponse = null;

        for (int round = 0; round < maxRounds; round++) {
            // 构建完整消息列表
            List<ObjectNode> fullMessages = buildFullMessages();

            // 获取工具 Schema
            List<ObjectNode> toolSchemas = getToolSchemas();

            // 调用 LLM
            LLMResponse response = llm.chat(fullMessages, toolSchemas, onToken);

            // 无工具调用 -> LLM 完成，返回文本
            if (!response.hasToolCalls()) {
                messages.add(response.toMessage());
                finalResponse = response.content();
                break;
            }

            // 有工具调用 -> 添加助手消息
            messages.add(response.toMessage());

            // 执行所有工具调用
            for (ToolCall tc : response.toolCalls()) {
                String result = executeTool(tc);
                addToolResult(tc, result);
                log.debug("Tool {} executed: {}", tc.name(), result.length() > 100 ? result.substring(0, 100) + "..." : result);
            }
        }

        if (finalResponse == null) {
            finalResponse = "(达到最大工具调用轮数)";
        }

        return finalResponse;
    }

    /**
     * 执行工具
     */
    private String executeTool(ToolCall tc) {
        Tool tool = tools.get(tc.name());
        if (tool == null) {
            return "Error: 未知工具 '" + tc.name() + "'";
        }

        try {
            return tool.execute(tc.arguments());
        } catch (Exception e) {
            log.error("Tool execution failed: {}", tc.name(), e);
            return "Error: " + e.getMessage();
        }
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
     * 构建完整消息列表（系统提示 + 历史）
     */
    private List<ObjectNode> buildFullMessages() {
        List<ObjectNode> full = new ArrayList<>();
        full.add(systemPrompt);
        full.addAll(messages);
        return full;
    }

    /**
     * 获取工具 Schema 列表
     */
    private List<ObjectNode> getToolSchemas() {
        List<ObjectNode> schemas = new ArrayList<>();
        for (Tool tool : tools.values()) {
            schemas.add(tool.toSchema());
        }
        return schemas;
    }

    /**
     * 构建系统提示
     */
    private ObjectNode buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个 AI 编程助手，可以帮助用户编写、修改和调试代码。\n\n");
        sb.append("可用工具：\n");

        for (Tool tool : tools.values()) {
            sb.append("- ").append(tool.getName()).append(": ").append(tool.getDescription()).append("\n");
        }

        sb.append("\n工作原则：\n");
        sb.append("1. 修改文件前总是先读取\n");
        sb.append("2. 使用 edit_file 进行小改动，它要求 old_string 必须在文件中唯一出现\n");
        sb.append("3. 修改后验证工作是否正确\n");

        ObjectNode msg = mapper.createObjectNode();
        msg.put("role", "system");
        msg.put("content", sb.toString());
        return msg;
    }

    /**
     * 重置对话历史
     */
    public void reset() {
        messages.clear();
    }

    /**
     * 获取消息历史
     */
    public List<ObjectNode> getMessages() {
        return messages;
    }

    // ==================== Builder ====================

    public static class Builder {
        private LLMClient llm;
        private final List<Tool> tools = new ArrayList<>();
        private int maxRounds = 30;

        /**
         * 设置 LLM 客户端（必需）
         */
        public Builder llm(LLMClient llm) {
            this.llm = llm;
            return this;
        }

        /**
         * 添加工具
         */
        public Builder tool(Tool tool) {
            this.tools.add(tool);
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
         * 构建 Agent 实例
         */
        public Agent build() {
            if (llm == null) {
                throw new IllegalStateException("LLMClient is required");
            }
            return new Agent(this);
        }
    }
}

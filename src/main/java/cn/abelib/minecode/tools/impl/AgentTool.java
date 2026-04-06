package cn.abelib.minecode.tools.impl;

import cn.abelib.minecode.agent.Agent;
import cn.abelib.minecode.llm.LLMClient;
import cn.abelib.minecode.tools.Tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 子代理工具 - 派生独立上下文的代理
 *
 * <p>设计思想：对于复杂的子任务，派生一个独立的代理，
 * 它有自己的对话历史和工具访问权限。这让主代理可以委托工作，
 * 如"研究这个代码库并汇报"，而不会污染自己的上下文窗口。
 *
 * <p>关键特性：
 * <ul>
 *   <li>独立上下文 - 子代理有自己的消息历史</li>
 *   <li>工具子集 - 子代理继承工具但不能递归创建子代理</li>
 *   <li>输出压缩 - 子代理结果可能被截断以保护主上下文</li>
 * </ul>
 *
 * @author Abel
 */
public class AgentTool extends Tool {
    private static final Logger log = LoggerFactory.getLogger(AgentTool.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    // 最大输出长度（保护主上下文）
    private static final int MAX_OUTPUT_LENGTH = 8000;

    // 子代理最大轮数
    private static final int SUB_AGENT_MAX_ROUNDS = 20;

    // 由 Agent 初始化时设置
    private LLMClient llmClient;
    private List<Tool> availableTools;
    private int maxContextTokens;

    public AgentTool() {
        super("agent", buildDescription(), buildParameters());
    }

    private static String buildDescription() {
        return """
                派生子代理独立处理复杂子任务。子代理有自己的上下文和工具访问权限。
                用于：研究代码库、独立实现多步骤变更、或任何需要全新上下文窗口的任务。
                子代理完成后会返回结果摘要。
                重要：子代理不能调用 agent 工具（避免无限递归）。""";
    }

    private static ObjectNode buildParameters() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode params = mapper.createObjectNode();
        params.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();

        ObjectNode task = mapper.createObjectNode();
        task.put("type", "string");
        task.put("description", "子代理应该完成的任务。描述要具体清晰。");
        properties.set("task", task);

        ObjectNode subagentType = mapper.createObjectNode();
        subagentType.put("type", "string");
        subagentType.put("description", "子代理类型（可选）: general-purpose, explore, plan");
        subagentType.put("default", "general-purpose");
        properties.set("subagent_type", subagentType);

        params.set("properties", properties);

        ArrayNode required = mapper.createArrayNode();
        required.add("task");
        params.set("required", required);

        return params;
    }

    /**
     * 初始化子代理环境
     *
     * @param llmClient        LLM 客户端
     * @param availableTools   可用工具列表
     * @param maxContextTokens 最大上下文 Token 数
     */
    public void initialize(LLMClient llmClient, List<Tool> availableTools, int maxContextTokens) {
        this.llmClient = llmClient;
        this.availableTools = availableTools;
        this.maxContextTokens = maxContextTokens;
    }

    @Override
    public String execute(JsonNode arguments) {
        if (llmClient == null) {
            return "错误: agent 工具未初始化（无 LLM 客户端）";
        }

        String task = arguments.path("task").asText();
        String subagentType = arguments.path("subagent_type").asText("general-purpose");

        if (task == null || task.trim().isEmpty()) {
            return "错误: 必须提供任务描述";
        }

        log.info("Starting sub-agent for task: {}", task.substring(0, Math.min(task.length(), 50)));

        try {
            // 创建工具子集（排除 agent 工具避免递归）
            List<Tool> subAgentTools = createSubAgentTools();

            // 创建子代理（使用 Builder 模式）
            Agent subAgent = Agent.builder()
                    .llm(llmClient)
                    .tools(subAgentTools)
                    .maxContextTokens(maxContextTokens)
                    .maxRounds(SUB_AGENT_MAX_ROUNDS)
                    .build();

            // 构建子代理的系统提示增强
            String enhancedTask = buildEnhancedTask(task, subagentType);

            // 执行子代理
            long startTime = System.currentTimeMillis();
            String result = subAgent.chat(enhancedTask);
            long duration = System.currentTimeMillis() - startTime;

            // 清理资源
            subAgent.close();

            // 压缩输出
            String compressedResult = compressOutput(result);

            log.info("Sub-agent completed in {}ms", duration);

            return formatResult(compressedResult, duration, subAgent.getMessages().size());

        } catch (Exception e) {
            log.error("Sub-agent failed", e);
            return "子代理错误: " + e.getMessage();
        }
    }

    /**
     * 创建子代理工具列表（排除 agent 工具）
     */
    private List<Tool> createSubAgentTools() {
        if (availableTools == null) {
            return new ArrayList<>();
        }

        return availableTools.stream()
                .filter(tool -> !(tool instanceof AgentTool))
                .collect(Collectors.toList());
    }

    /**
     * 构建增强的任务描述
     */
    private String buildEnhancedTask(String task, String subagentType) {
        StringBuilder sb = new StringBuilder();

        sb.append(task).append("\n\n");

        // 根据子代理类型添加特定指导
        switch (subagentType.toLowerCase()) {
            case "explore" -> {
                sb.append("你是探索型子代理。你的任务是快速搜索和浏览代码库。\n");
                sb.append("优先使用 glob 和 grep 工具找到相关文件。\n");
                sb.append("简要报告你的发现，列出关键文件路径和摘要。\n");
            }
            case "plan" -> {
                sb.append("你是规划型子代理。你的任务是分析需求并制定实现计划。\n");
                sb.append("阅读相关代码，理解现有架构，然后给出分步实现计划。\n");
                sb.append("不要实际修改代码，只输出计划。\n");
            }
            default -> {
                sb.append("你是通用型子代理。完成任务后简要报告结果。\n");
            }
        }

        sb.append("\n重要提示：你无法调用 agent 工具创建更多子代理。");

        return sb.toString();
    }

    /**
     * 压缩输出以保护主上下文
     */
    private String compressOutput(String result) {
        if (result == null) {
            return "(子代理无输出)";
        }

        if (result.length() <= MAX_OUTPUT_LENGTH) {
            return result;
        }

        // 保留开头和结尾
        int headLength = MAX_OUTPUT_LENGTH * 2 / 3;
        int tailLength = MAX_OUTPUT_LENGTH / 3;

        return result.substring(0, headLength) +
                "\n\n... (子代理输出已压缩，省略 " +
                (result.length() - MAX_OUTPUT_LENGTH) + " 字符) ...\n\n" +
                result.substring(result.length() - tailLength);
    }

    /**
     * 格式化结果
     */
    private String formatResult(String result, long durationMs, int messageCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("┌─────────────────────────────────────────────┐\n");
        sb.append("│ 子代理完成                                    │\n");
        sb.append("├─────────────────────────────────────────────┤\n");
        sb.append(String.format("│ 耗时: %-37s │\n", durationMs + "ms"));
        sb.append(String.format("│ 消息数: %-35s │\n", messageCount));
        sb.append("└─────────────────────────────────────────────┘\n\n");
        sb.append(result);
        return sb.toString();
    }
}

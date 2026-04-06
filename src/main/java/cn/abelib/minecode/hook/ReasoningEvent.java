package cn.abelib.minecode.hook;

import cn.abelib.minecode.agent.Agent;

/**
 * 推理相关事件的抽象基类 (Sealed Class)
 *
 * <p>共享推理相关的上下文信息，包括：
 * <ul>
 *   <li>{@link #getModelName()} - 使用的模型名称</li>
 *   <li>{@link #getAgent()} - Agent 实例</li>
 *   <li>{@link #getTimestamp()} - 事件时间戳</li>
 * </ul>
 *
 * <p>子类包括：
 * <ul>
 *   <li>{@link PreReasoningEvent} - LLM 推理前事件</li>
 *   <li>{@link PostReasoningEvent} - LLM 推理后事件</li>
 *   <li>{@link ReasoningChunkEvent} - 流式推理 Token 事件</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * // 匹配所有推理事件
 * if (event instanceof ReasoningEvent e) {
 *     System.out.println("Model: " + e.getModelName());
 * }
 *
 * // switch 模式匹配
 * switch (event) {
 *     case PreReasoningEvent e -> prepareForReasoning(e);
 *     case PostReasoningEvent e -> processReasoningResult(e);
 *     case ReasoningChunkEvent e -> displayToken(e.getToken());
 *     case ReasoningEvent e -> handleOtherReasoning(e);  // 兜底
 *     ...
 * }
 * }</pre>
 *
 * @author Abel
 */
public abstract sealed class ReasoningEvent extends HookEvent
        permits PreReasoningEvent, PostReasoningEvent, ReasoningChunkEvent {

    private final String modelName;

    protected ReasoningEvent(Agent agent, String modelName) {
        super(agent);
        this.modelName = modelName != null ? modelName : "unknown";
    }

    /**
     * 获取使用的模型名称
     *
     * @return 模型名称（如 "gpt-4o", "claude-sonnet-4.6"）
     */
    public final String getModelName() {
        return modelName;
    }
}

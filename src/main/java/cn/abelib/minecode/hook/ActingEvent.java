package cn.abelib.minecode.hook;

import cn.abelib.minecode.agent.Agent;
import cn.abelib.minecode.llm.ToolCall;

/**
 * 工具执行相关事件的抽象基类 (Sealed Class)
 *
 * <p>共享工具执行相关的上下文信息，包括：
 * <ul>
 *   <li>{@link #getToolCall()} - 工具调用信息</li>
 *   <li>{@link #getAgent()} - Agent 实例</li>
 *   <li>{@link #getTimestamp()} - 事件时间戳</li>
 * </ul>
 *
 * <p>子类包括：
 * <ul>
 *   <li>{@link PreActingEvent} - 工具执行前事件（可修改参数）</li>
 *   <li>{@link PostActingEvent} - 工具执行后事件（可修改结果）</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * // 匹配所有工具执行事件
 * if (event instanceof ActingEvent e) {
 *     System.out.println("Tool: " + e.getToolCall().name());
 * }
 *
 * // switch 模式匹配
 * switch (event) {
 *     case PreActingEvent e -> validateToolCall(e);
 *     case PostActingEvent e -> processToolResult(e);
 *     case ActingEvent e -> handleOtherActing(e);  // 兜底
 *     ...
 * }
 * }</pre>
 *
 * @author Abel
 */
public abstract sealed class ActingEvent extends HookEvent
        permits PreActingEvent, PostActingEvent {

    protected ToolCall toolCall;

    protected ActingEvent(Agent agent, ToolCall toolCall) {
        super(agent);
        this.toolCall = toolCall;
    }

    /**
     * 获取工具调用信息
     *
     * @return 工具调用对象，包含工具名称和参数
     */
    public final ToolCall getToolCall() {
        return toolCall;
    }
}

package cn.abelib.minecode.hook;

import cn.abelib.minecode.agent.Agent;

import java.util.HashMap;
import java.util.Map;

/**
 * Hook 事件基类 (Sealed Class)
 *
 * <p>使用 Java 17 sealed class 限制继承，提供编译时类型安全。
 * 所有 Hook 事件必须继承此类，并属于以下分类之一：
 * <ul>
 *   <li>{@link PreCallEvent} - Agent 调用前事件</li>
 *   <li>{@link PostCallEvent} - Agent 调用后事件</li>
 *   <li>{@link ReasoningEvent} - 推理相关事件（抽象层）</li>
 *   <li>{@link ActingEvent} - 工具执行相关事件（抽象层）</li>
 *   <li>{@link ErrorEvent} - 错误事件</li>
 *   <li>{@link InterruptedEvent} - 中断事件</li>
 * </ul>
 *
 * <p>使用 switch 模式匹配示例：
 * <pre>{@code
 * switch (event) {
 *     case PreReasoningEvent e -> handlePreReasoning(e);
 *     case PostReasoningEvent e -> handlePostReasoning(e);
 *     case PreActingEvent e -> handlePreActing(e);
 *     case ActingEvent e -> handleAnyActing(e);  // 匹配所有 Acting 事件
 *     case ReasoningEvent e -> handleAnyReasoning(e);  // 匹配所有 Reasoning 事件
 *     case InterruptedEvent e -> handleInterrupt(e);  // 中断处理
 *     default -> handleOther(e);
 * }
 * }</pre>
 *
 * @author Abel
 * @see ReasoningEvent
 * @see ActingEvent
 */
public abstract sealed class HookEvent
        permits PreCallEvent, PostCallEvent, ReasoningEvent, ActingEvent, ErrorEvent, InterruptedEvent {

    private final long timestamp;
    private final Agent agent;
    private final Map<String, Object> context;
    private boolean stopped = false;

    protected HookEvent(Agent agent) {
        this.timestamp = System.currentTimeMillis();
        this.agent = agent;
        this.context = new HashMap<>();
    }

    /**
     * 获取事件时间戳
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * 获取触发事件的 Agent
     */
    public Agent getAgent() {
        return agent;
    }

    /**
     * 设置上下文数据
     */
    public void putContext(String key, Object value) {
        context.put(key, value);
    }

    /**
     * 获取上下文数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getContext(String key, Class<T> type) {
        Object value = context.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * 获取所有上下文
     */
    public Map<String, Object> getContext() {
        return context;
    }

    /**
     * 停止 Agent 执行（用于 HITL 中断）
     */
    public void stopAgent() {
        this.stopped = true;
    }

    /**
     * 是否已停止
     */
    public boolean isStopped() {
        return stopped;
    }

    /**
     * 获取事件类型名称
     */
    public abstract String getEventType();
}

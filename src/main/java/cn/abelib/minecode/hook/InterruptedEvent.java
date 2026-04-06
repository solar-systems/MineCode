package cn.abelib.minecode.hook;

import cn.abelib.minecode.agent.Agent;
import cn.abelib.minecode.agent.InterruptReason;

/**
 * 中断事件 - Agent 执行被中断时触发
 *
 * <p><b>通知事件：</b>只读，用于中断处理和日志记录。
 *
 * <p>使用场景：
 * <ul>
 *   <li>中断日志记录</li>
 *   <li>中断通知</li>
 *   <li>中断统计</li>
 *   <li>清理资源</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * Hook interruptHook = new Hook() {
 *     @Override
 *     public HookEvent onEvent(HookEvent event) {
 *         if (event instanceof InterruptedEvent e) {
 *             log.warn("Agent interrupted: {} - {}",
 *                 e.getReason().getDisplayName(),
 *                 e.getMessage());
 *
 *             // 根据中断原因处理
 *             if (e.getReason().isRecoverable()) {
 *                 // 尝试恢复
 *                 attemptRecovery(e);
 *             }
 *         }
 *         return event;
 *     }
 * };
 * }</pre>
 *
 * @author Abel
 */
public final class InterruptedEvent extends HookEvent {

    private final String message;
    private final InterruptReason reason;
    private final Throwable cause;
    private final long executionTimeMs;

    /**
     * 创建中断事件
     *
     * @param agent            Agent 实例
     * @param message          中断消息
     * @param reason           中断原因
     * @param cause            中断异常（可为 null）
     * @param executionTimeMs  已执行时间（毫秒）
     */
    public InterruptedEvent(Agent agent, String message, InterruptReason reason,
                           Throwable cause, long executionTimeMs) {
        super(agent);
        this.message = message;
        this.reason = reason;
        this.cause = cause;
        this.executionTimeMs = executionTimeMs;
    }

    /**
     * 获取中断消息
     */
    public String getMessage() {
        return message;
    }

    /**
     * 获取中断原因
     */
    public InterruptReason getReason() {
        return reason;
    }

    /**
     * 获取中断异常（可为 null）
     */
    public Throwable getCause() {
        return cause;
    }

    /**
     * 获取已执行时间（毫秒）
     */
    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    /**
     * 是否可恢复
     */
    public boolean isRecoverable() {
        return reason.isRecoverable();
    }

    @Override
    public String getEventType() {
        return "INTERRUPTED";
    }

    @Override
    public String toString() {
        return String.format("InterruptedEvent[reason=%s, message=%s, executionTime=%dms]",
                reason.getDisplayName(), message, executionTimeMs);
    }
}

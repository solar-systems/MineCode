package cn.abelib.minecode.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 中断上下文 - 支持 Agent 执行过程中的外部中断
 *
 * <p>使用场景：
 * <ul>
 *   <li>用户取消操作</li>
 *   <li>超时中断</li>
 *   <li>外部信号中断</li>
 *   <li>Hook 中断（权限拒绝、循环检测）</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * Agent agent = Agent.builder()
 *     .llm(llm)
 *     .build();
 *
 * // 在另一个线程中中断（超时）
 * new Thread(() -> {
 *     Thread.sleep(30000);  // 30秒后中断
 *     agent.interrupt("操作超时", InterruptReason.TIMEOUT);
 * }).start();
 *
 * // 用户取消
 * agent.interrupt("用户取消操作", InterruptReason.USER_CANCEL);
 *
 * // Hook 中断
 * agent.interrupt("检测到无限循环", InterruptReason.HOOK_INTERRUPT);
 *
 * // 检查中断状态
 * if (agent.isInterrupted()) {
 *     InterruptContext ctx = agent.getInterruptContext();
 *     System.out.println("Agent was interrupted: " + ctx.getMessage());
 *     System.out.println("Reason: " + ctx.getReason().getDisplayName());
 *     if (ctx.getReason().isRecoverable()) {
 *         // 尝试恢复
 *     }
 * }
 * }</pre>
 *
 * @author Abel
 */
public class InterruptContext {

    private static final Logger log = LoggerFactory.getLogger(InterruptContext.class);

    private final AtomicBoolean interrupted = new AtomicBoolean(false);
    private final AtomicReference<String> interruptMessage = new AtomicReference<>();
    private final AtomicReference<InterruptReason> interruptReason = new AtomicReference<>();
    private final AtomicReference<Throwable> interruptCause = new AtomicReference<>();
    private final AtomicLong executionStartTime = new AtomicLong(0);
    private final AtomicReference<InterruptListener> listener = new AtomicReference<>();

    /**
     * 中断监听器
     */
    @FunctionalInterface
    public interface InterruptListener {
        /**
         * 中断发生时的回调
         *
         * @param message 中断消息
         * @param reason  中断原因枚举
         * @param cause   中断异常（可为 null）
         */
        void onInterrupt(String message, InterruptReason reason, Throwable cause);
    }

    /**
     * 标记执行开始（用于计算执行时间）
     */
    public void markExecutionStart() {
        executionStartTime.set(System.currentTimeMillis());
    }

    /**
     * 获取已执行时间（毫秒）
     */
    public long getExecutionTimeMs() {
        long start = executionStartTime.get();
        return start > 0 ? System.currentTimeMillis() - start : 0;
    }

    /**
     * 中断 Agent 执行
     *
     * @param message 中断消息
     */
    public void interrupt(String message) {
        interrupt(message, InterruptReason.UNKNOWN, null);
    }

    /**
     * 中断 Agent 执行（带原因枚举）
     *
     * @param message 中断消息
     * @param reason  中断原因
     */
    public void interrupt(String message, InterruptReason reason) {
        interrupt(message, reason, null);
    }

    /**
     * 中断 Agent 执行（带原因和异常）
     *
     * @param message 中断消息
     * @param cause   中断异常
     */
    public void interrupt(String message, Throwable cause) {
        interrupt(message, cause != null ? InterruptReason.ERROR : InterruptReason.UNKNOWN, cause);
    }

    /**
     * 中断 Agent 执行（完整参数）
     *
     * @param message 中断消息
     * @param reason  中断原因
     * @param cause   中断异常（可为 null）
     */
    public void interrupt(String message, InterruptReason reason, Throwable cause) {
        if (interrupted.compareAndSet(false, true)) {
            this.interruptMessage.set(message);
            this.interruptReason.set(reason != null ? reason : InterruptReason.UNKNOWN);
            this.interruptCause.set(cause);

            log.info("Agent interrupted: {} (reason={})", message, reason);

            // 触发监听器
            InterruptListener l = listener.get();
            if (l != null) {
                try {
                    l.onInterrupt(message, this.interruptReason.get(), cause);
                } catch (Exception e) {
                    log.warn("Interrupt listener threw exception", e);
                }
            }
        }
    }

    /**
     * 检查中断状态，如果已中断则抛出异常
     *
     * @throws InterruptedException 如果已被中断
     */
    public void checkInterrupted() throws InterruptedException {
        if (interrupted.get()) {
            String message = interruptMessage.get();
            throw new InterruptedException(message != null ? message : "Agent was interrupted");
        }
    }

    /**
     * 检查中断状态，如果已中断则抛出运行时异常
     *
     * @throws AgentInterruptedException 如果已被中断
     */
    public void checkInterruptedRuntime() {
        if (interrupted.get()) {
            throw new AgentInterruptedException(
                    interruptMessage.get(),
                    interruptReason.get(),
                    interruptCause.get()
            );
        }
    }

    /**
     * 是否已被中断
     */
    public boolean isInterrupted() {
        return interrupted.get();
    }

    /**
     * 获取中断消息
     */
    public String getMessage() {
        return interruptMessage.get();
    }

    /**
     * 获取中断消息（兼容旧 API）
     */
    public String getInterruptMessage() {
        return interruptMessage.get();
    }

    /**
     * 获取中断原因
     */
    public InterruptReason getReason() {
        InterruptReason reason = interruptReason.get();
        return reason != null ? reason : InterruptReason.UNKNOWN;
    }

    /**
     * 获取中断异常
     */
    public Throwable getCause() {
        return interruptCause.get();
    }

    /**
     * 获取中断异常（兼容旧 API）
     */
    public Throwable getInterruptCause() {
        return interruptCause.get();
    }

    /**
     * 是否可恢复
     */
    public boolean isRecoverable() {
        return getReason().isRecoverable();
    }

    /**
     * 重置中断状态（用于新的执行）
     */
    public void reset() {
        interrupted.set(false);
        interruptMessage.set(null);
        interruptReason.set(null);
        interruptCause.set(null);
        executionStartTime.set(0);
        log.debug("Interrupt context reset");
    }

    /**
     * 设置中断监听器
     *
     * @param listener 监听器
     */
    public void setListener(InterruptListener listener) {
        this.listener.set(listener);
    }

    /**
     * 获取当前状态描述
     */
    public String getStatus() {
        if (!interrupted.get()) {
            return "running";
        }
        InterruptReason reason = interruptReason.get();
        String message = interruptMessage.get();
        return String.format("interrupted: %s - %s",
                reason != null ? reason.getDisplayName() : "unknown",
                message != null ? message : "");
    }

    @Override
    public String toString() {
        return "InterruptContext{" +
                "interrupted=" + interrupted.get() +
                ", reason=" + interruptReason.get() +
                ", message='" + interruptMessage.get() + '\'' +
                ", executionTime=" + getExecutionTimeMs() + "ms" +
                '}';
    }

    /**
     * Agent 中断异常
     */
    public static class AgentInterruptedException extends RuntimeException {
        private final String interruptMessage;
        private final InterruptReason reason;

        public AgentInterruptedException(String message) {
            this(message, InterruptReason.UNKNOWN, null);
        }

        public AgentInterruptedException(String message, Throwable cause) {
            this(message, InterruptReason.ERROR, cause);
        }

        public AgentInterruptedException(String message, InterruptReason reason, Throwable cause) {
            super(message, cause);
            this.interruptMessage = message;
            this.reason = reason != null ? reason : InterruptReason.UNKNOWN;
        }

        public String getInterruptMessage() {
            return interruptMessage;
        }

        public InterruptReason getReason() {
            return reason;
        }
    }
}

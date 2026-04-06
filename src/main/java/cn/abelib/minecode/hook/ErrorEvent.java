package cn.abelib.minecode.hook;

import cn.abelib.minecode.agent.Agent;

/**
 * 错误事件
 *
 * <p><b>通知事件：</b>只读，用于错误处理和日志记录。
 *
 * <p>使用场景：
 * <ul>
 *   <li>错误日志记录</li>
 *   <li>错误通知</li>
 *   <li>错误统计</li>
 *   <li>自定义错误处理</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * Hook errorHook = new Hook() {
 *     @Override
 *     public HookEvent onEvent(HookEvent event) {
 *         if (event instanceof ErrorEvent e) {
 *             log.error("[{}] 错误: {}", e.getPhase(), e.getError().getMessage());
 *             // 发送通知
 *             sendNotification(e.getError());
 *         }
 *         return event;
 *     }
 * };
 * }</pre>
 */
public final class ErrorEvent extends HookEvent {

    private final Throwable error;
    private final String phase;

    /**
     * 创建错误事件
     *
     * @param agent Agent 实例
     * @param error 错误对象
     * @param phase 错误发生阶段（如 "reasoning", "acting:bash"）
     */
    public ErrorEvent(Agent agent, Throwable error, String phase) {
        super(agent);
        this.error = error;
        this.phase = phase;
    }

    /**
     * 获取错误对象
     */
    public Throwable getError() {
        return error;
    }

    /**
     * 获取错误发生阶段
     *
     * @return 阶段名称（如 "reasoning", "acting:bash"）
     */
    public String getPhase() {
        return phase;
    }

    /**
     * 获取错误消息
     */
    public String getErrorMessage() {
        return error != null ? error.getMessage() : "Unknown error";
    }

    @Override
    public String getEventType() {
        return "ERROR";
    }
}

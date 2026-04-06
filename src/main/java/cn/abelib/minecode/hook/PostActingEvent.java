package cn.abelib.minecode.hook;

import cn.abelib.minecode.agent.Agent;
import cn.abelib.minecode.llm.ToolCall;

/**
 * 工具执行后事件
 *
 * <p><b>可修改事件：</b>可以修改执行结果，影响 Agent 行为。
 *
 * <p>使用场景：
 * <ul>
 *   <li>结果转换或过滤</li>
 *   <li>错误重试（修改结果后重试）</li>
 *   <li>结果缓存</li>
 *   <li>执行时间统计</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * Hook retryHook = new Hook() {
 *     @Override
 *     public HookEvent onEvent(HookEvent event) {
 *         if (event instanceof PostActingEvent e) {
 *             if (!e.isSuccess() && shouldRetry(e.getToolCall().name())) {
 *                 // 重试逻辑
 *                 String newResult = retryExecution(e.getToolCall());
 *                 e.setResult(newResult);
 *                 e.setSuccess(!newResult.startsWith("Error"));
 *             }
 *         }
 *         return event;
 *     }
 * };
 * }</pre>
 */
public final class PostActingEvent extends ActingEvent {

    private String result;
    private long durationMs;
    private boolean success;

    /**
     * 创建工具执行后事件
     *
     * @param agent     Agent 实例
     * @param toolCall  工具调用信息
     * @param result    执行结果
     * @param durationMs 执行耗时（毫秒）
     * @param success   是否成功
     */
    public PostActingEvent(Agent agent, ToolCall toolCall, String result,
                           long durationMs, boolean success) {
        super(agent, toolCall);
        this.result = result;
        this.durationMs = durationMs;
        this.success = success;
    }

    /**
     * 获取执行结果
     */
    public String getResult() {
        return result;
    }

    /**
     * 设置执行结果（修改结果）
     *
     * @param result 新的执行结果
     */
    public void setResult(String result) {
        this.result = result;
    }

    /**
     * 获取执行耗时（毫秒）
     */
    public long getDurationMs() {
        return durationMs;
    }

    /**
     * 设置执行耗时
     *
     * @param durationMs 执行耗时（毫秒）
     */
    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    /**
     * 是否执行成功
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 设置执行状态
     *
     * @param success 是否成功
     */
    public void setSuccess(boolean success) {
        this.success = success;
    }

    @Override
    public String getEventType() {
        return "POST_ACTING";
    }
}

package cn.abelib.minecode.hook;

import cn.abelib.minecode.agent.Agent;

/**
 * Agent 调用后事件
 *
 * <p><b>可修改事件：</b>可以修改最终响应。
 *
 * <p>使用场景：
 * <ul>
 *   <li>响应后处理</li>
 *   <li>统计收集</li>
 *   <li>会话清理</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * Hook statsHook = new Hook() {
 *     @Override
 *     public HookEvent onEvent(HookEvent event) {
 *         if (event instanceof PostCallEvent e) {
 *             System.out.println("完成! 使用 " + e.getRoundsUsed() + " 轮");
 *         }
 *         return event;
 *     }
 * };
 * }</pre>
 */
public final class PostCallEvent extends HookEvent {

    private String response;
    private final int roundsUsed;

    /**
     * 创建 Agent 调用后事件
     *
     * @param agent      Agent 实例
     * @param response   最终响应
     * @param roundsUsed 使用的轮数
     */
    public PostCallEvent(Agent agent, String response, int roundsUsed) {
        super(agent);
        this.response = response;
        this.roundsUsed = roundsUsed;
    }

    /**
     * 获取最终响应
     */
    public String getResponse() {
        return response;
    }

    /**
     * 设置最终响应（修改响应）
     *
     * @param response 新的响应
     */
    public void setResponse(String response) {
        this.response = response;
    }

    /**
     * 获取使用的轮数
     */
    public int getRoundsUsed() {
        return roundsUsed;
    }

    @Override
    public String getEventType() {
        return "POST_CALL";
    }
}

package cn.abelib.minecode.hook;

import cn.abelib.minecode.agent.Agent;
import cn.abelib.minecode.llm.ToolCall;

/**
 * 工具执行前事件
 *
 * <p><b>可修改事件：</b>可以修改工具调用参数，影响工具执行。
 *
 * <p>使用场景：
 * <ul>
 *   <li>参数验证和修正</li>
 *   <li>权限检查（HITL）</li>
 *   <li>参数注入（如添加认证信息）</li>
 *   <li>工具调用审计</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * Hook hitlHook = new Hook() {
 *     @Override
 *     public HookEvent onEvent(HookEvent event) {
 *         if (event instanceof PreActingEvent e) {
 *             ToolCall tc = e.getToolCall();
 *             if (tc.name().equals("bash") && tc.arguments().toString().contains("rm -rf")) {
 *                 // 用户确认
 *                 if (!confirm("确定要执行危险命令吗？")) {
 *                     event.stopAgent();
 *                 }
 *             }
 *         }
 *         return event;
 *     }
 * };
 * }</pre>
 */
public final class PreActingEvent extends ActingEvent {

    /**
     * 创建工具执行前事件
     *
     * @param agent    Agent 实例
     * @param toolCall 工具调用信息
     */
    public PreActingEvent(Agent agent, ToolCall toolCall) {
        super(agent, toolCall);
    }

    /**
     * 设置工具调用（修改参数）
     *
     * <p>用于修正工具参数后再执行。
     *
     * @param toolCall 修改后的工具调用
     */
    public void setToolCall(ToolCall toolCall) {
        this.toolCall = toolCall;
    }

    @Override
    public String getEventType() {
        return "PRE_ACTING";
    }
}

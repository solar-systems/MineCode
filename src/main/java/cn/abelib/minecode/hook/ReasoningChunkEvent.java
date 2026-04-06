package cn.abelib.minecode.hook;

import cn.abelib.minecode.agent.Agent;

/**
 * 流式推理 Token 事件
 *
 * <p><b>通知事件：</b>只读，用于实时显示推理过程。
 *
 * <p>使用场景：
 * <ul>
 *   <li>实时显示 LLM 输出</li>
 *   <li>流式响应处理</li>
 *   <li>进度指示</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * Hook streamingHook = new Hook() {
 *     @Override
 *     public HookEvent onEvent(HookEvent event) {
 *         if (event instanceof ReasoningChunkEvent e) {
 *             if (e.isFirst()) {
 *                 System.out.print("\n[AI] ");
 *             }
 *             System.out.print(e.getToken());
 *         }
 *         return event;
 *     }
 * };
 * }</pre>
 */
public final class ReasoningChunkEvent extends ReasoningEvent {

    private final String token;
    private final boolean isFirst;

    /**
     * 创建流式 Token 事件
     *
     * @param agent   Agent 实例
     * @param token   Token 内容
     * @param isFirst 是否是第一个 Token
     */
    public ReasoningChunkEvent(Agent agent, String token, boolean isFirst) {
        super(agent, null);
        this.token = token;
        this.isFirst = isFirst;
    }

    /**
     * 创建流式 Token 事件（带模型名称）
     *
     * @param agent     Agent 实例
     * @param token     Token 内容
     * @param isFirst   是否是第一个 Token
     * @param modelName 模型名称
     */
    public ReasoningChunkEvent(Agent agent, String token, boolean isFirst, String modelName) {
        super(agent, modelName);
        this.token = token;
        this.isFirst = isFirst;
    }

    /**
     * 获取 Token 内容
     */
    public String getToken() {
        return token;
    }

    /**
     * 是否是第一个 Token
     */
    public boolean isFirst() {
        return isFirst;
    }

    @Override
    public String getEventType() {
        return "REASONING_CHUNK";
    }
}

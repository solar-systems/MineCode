package cn.abelib.minecode.hook;

import cn.abelib.minecode.agent.Agent;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * 推理前事件（调用 LLM 前）
 *
 * <p><b>可修改事件：</b>可以修改消息列表，影响 LLM 输入。
 *
 * <p>使用场景：
 * <ul>
 *   <li>注入系统提示或上下文</li>
 *   <li>修改或过滤消息历史</li>
 *   <li>添加思考提示（如 "Think step by step"）</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * Hook hintInjector = new Hook() {
 *     @Override
 *     public HookEvent onEvent(HookEvent event) {
 *         if (event instanceof PreReasoningEvent e) {
 *             // 注入系统提示
 *             List<ObjectNode> msgs = new ArrayList<>(e.getMessages());
 *             msgs.add(0, createSystemHint("Think step by step"));
 *             e.setMessages(msgs);
 *         }
 *         return event;
 *     }
 * };
 * }</pre>
 */
public final class PreReasoningEvent extends ReasoningEvent {

    private List<ObjectNode> messages;

    /**
     * 创建推理前事件
     *
     * @param agent     Agent 实例
     * @param messages  消息列表
     */
    public PreReasoningEvent(Agent agent, List<ObjectNode> messages) {
        this(agent, messages, null);
    }

    /**
     * 创建推理前事件（带模型名称）
     *
     * @param agent     Agent 实例
     * @param messages  消息列表
     * @param modelName 模型名称
     */
    public PreReasoningEvent(Agent agent, List<ObjectNode> messages, String modelName) {
        super(agent, modelName);
        this.messages = messages;
    }

    /**
     * 获取消息列表
     *
     * @return 消息列表
     */
    public List<ObjectNode> getMessages() {
        return messages;
    }

    /**
     * 设置消息列表（修改 LLM 输入）
     *
     * @param messages 新的消息列表
     */
    public void setMessages(List<ObjectNode> messages) {
        this.messages = messages;
    }

    @Override
    public String getEventType() {
        return "PRE_REASONING";
    }
}

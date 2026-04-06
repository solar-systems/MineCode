package cn.abelib.minecode.hook;

import cn.abelib.minecode.agent.Agent;

/**
 * Agent 调用前事件
 *
 * <p><b>可修改事件：</b>可以修改用户输入，影响 Agent 处理。
 *
 * <p>使用场景：
 * <ul>
 *   <li>输入预处理</li>
 *   <li>上下文注入</li>
 *   <li>请求过滤</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * Hook inputPreprocessor = new Hook() {
 *     @Override
 *     public HookEvent onEvent(HookEvent event) {
 *         if (event instanceof PreCallEvent e) {
 *             // 预处理用户输入
 *             String processed = preprocessInput(e.getUserInput());
 *             e.setUserInput(processed);
 *         }
 *         return event;
 *     }
 * };
 * }</pre>
 */
public final class PreCallEvent extends HookEvent {

    private String userInput;

    /**
     * 创建 Agent 调用前事件
     *
     * @param agent     Agent 实例
     * @param userInput 用户输入
     */
    public PreCallEvent(Agent agent, String userInput) {
        super(agent);
        this.userInput = userInput;
    }

    /**
     * 获取用户输入
     */
    public String getUserInput() {
        return userInput;
    }

    /**
     * 设置用户输入（修改输入）
     *
     * @param userInput 新的用户输入
     */
    public void setUserInput(String userInput) {
        this.userInput = userInput;
    }

    @Override
    public String getEventType() {
        return "PRE_CALL";
    }
}

package cn.abelib.minecode.hook;

import cn.abelib.minecode.agent.Agent;
import cn.abelib.minecode.llm.ToolCall;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 推理后事件（LLM 返回后）
 *
 * <p><b>控制流能力：</b>支持 {@link #gotoReasoning(List)} 跳回推理阶段。
 *
 * <p>使用场景：
 * <ul>
 *   <li>统计 Token 使用量</li>
 *   <li>记录推理结果</li>
 *   <li>修正工具调用参数后重试（通过 gotoReasoning）</li>
 *   <li>检测无限循环模式</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * Hook autoCorrectHook = new Hook() {
 *     @Override
 *     public HookEvent onEvent(HookEvent event) {
 *         if (event instanceof PostReasoningEvent e) {
 *             if (e.hasToolCalls()) {
 *                 for (ToolCall tc : e.getToolCalls()) {
 *                     if (tc.name().equals("edit_file") && !isUnique(tc)) {
 *                         // 自动修正参数后重试
 *                         List<ObjectNode> corrected = correctOldString(tc);
 *                         e.gotoReasoning(corrected);
 *                         break;
 *                     }
 *                 }
 *             }
 *         }
 *         return event;
 *     }
 * };
 * }</pre>
 */
public final class PostReasoningEvent extends ReasoningEvent {

    private String content;
    private List<ToolCall> toolCalls;
    private long promptTokens;
    private long completionTokens;

    // 控制流：跳回推理阶段
    private boolean gotoReasoningRequested = false;
    private List<ObjectNode> gotoReasoningMessages = null;

    /**
     * 创建推理后事件
     *
     * @param agent            Agent 实例
     * @param content          LLM 返回的文本内容
     * @param toolCalls        工具调用列表
     * @param promptTokens     输入 Token 数
     * @param completionTokens 输出 Token 数
     */
    public PostReasoningEvent(Agent agent, String content, List<ToolCall> toolCalls,
                              long promptTokens, long completionTokens) {
        this(agent, content, toolCalls, promptTokens, completionTokens, null);
    }

    /**
     * 创建推理后事件（带模型名称）
     *
     * @param agent            Agent 实例
     * @param content          LLM 返回的文本内容
     * @param toolCalls        工具调用列表
     * @param promptTokens     输入 Token 数
     * @param completionTokens 输出 Token 数
     * @param modelName        模型名称
     */
    public PostReasoningEvent(Agent agent, String content, List<ToolCall> toolCalls,
                              long promptTokens, long completionTokens, String modelName) {
        super(agent, modelName);
        this.content = content;
        this.toolCalls = toolCalls;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
    }

    /**
     * 获取 LLM 返回的文本内容
     */
    public String getContent() {
        return content;
    }

    /**
     * 获取工具调用列表
     */
    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    /**
     * 是否有工具调用
     */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    /**
     * 获取输入 Token 数
     */
    public long getPromptTokens() {
        return promptTokens;
    }

    /**
     * 获取输出 Token 数
     */
    public long getCompletionTokens() {
        return completionTokens;
    }

    /**
     * 获取总 Token 数
     */
    public long getTotalTokens() {
        return promptTokens + completionTokens;
    }

    // ==================== 控制流能力 ====================

    /**
     * 请求跳回推理阶段
     *
     * <p>用于实现：
     * <ul>
     *   <li>修正工具调用参数后重试</li>
     *   <li>注入额外上下文后重新推理</li>
     *   <li>自动错误修正</li>
     * </ul>
     *
     * @param messages 新的消息列表（将替换当前消息历史）
     */
    public void gotoReasoning(List<ObjectNode> messages) {
        this.gotoReasoningRequested = true;
        this.gotoReasoningMessages = new ArrayList<>(messages);
    }

    /**
     * 是否请求跳回推理阶段
     */
    public boolean isGotoReasoningRequested() {
        return gotoReasoningRequested;
    }

    /**
     * 获取跳回推理的消息列表
     */
    public List<ObjectNode> getGotoReasoningMessages() {
        return gotoReasoningMessages;
    }

    /**
     * 清除跳回推理请求
     */
    public void clearGotoReasoning() {
        this.gotoReasoningRequested = false;
        this.gotoReasoningMessages = null;
    }

    @Override
    public String getEventType() {
        return "POST_REASONING";
    }
}

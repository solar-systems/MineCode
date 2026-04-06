package cn.abelib.minecode.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * LLM 响应
 *
 * @param content          响应内容
 * @param toolCalls        工具调用列表
 * @param promptTokens     输入 Token 数
 * @param completionTokens 输出 Token 数
 * @author Abel
 */
public record LLMResponse(
        String content,
        List<ToolCall> toolCalls,
        long promptTokens,
        long completionTokens
) {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * 是否有工具调用
     */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    /**
     * 转换为消息格式（用于追加到历史记录）
     */
    public ObjectNode toMessage() {
        ObjectNode msg = mapper.createObjectNode();
        msg.put("role", "assistant");

        if (content != null && !content.isEmpty()) {
            msg.put("content", content);
        } else {
            msg.putNull("content");
        }

        if (hasToolCalls()) {
            var toolCallsArray = msg.putArray("tool_calls");
            for (ToolCall tc : toolCalls) {
                ObjectNode tcNode = mapper.createObjectNode();
                tcNode.put("id", tc.id());
                tcNode.put("type", "function");

                ObjectNode funcNode = mapper.createObjectNode();
                funcNode.put("name", tc.name());
                funcNode.put("arguments", tc.arguments().toString());

                tcNode.set("function", funcNode);
                toolCallsArray.add(tcNode);
            }
        }

        return msg;
    }
}

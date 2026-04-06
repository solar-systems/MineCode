package cn.abelib.minecode.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 工具调用
 *
 * @param id        调用 ID
 * @param name      工具名称
 * @param arguments 参数
 * @author Abel
 */
public record ToolCall(String id, String name, JsonNode arguments) {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * 转换为 OpenAI 消息格式
     */
    public ObjectNode toMessage() {
        ObjectNode msg = mapper.createObjectNode();
        msg.put("role", "assistant");

        ObjectNode toolCall = mapper.createObjectNode();
        toolCall.put("id", id);
        toolCall.put("type", "function");

        ObjectNode function = mapper.createObjectNode();
        function.put("name", name);
        function.put("arguments", arguments.toString());

        toolCall.set("function", function);

        ArrayNode toolCalls = msg.putArray("tool_calls");
        toolCalls.add(toolCall);

        return msg;
    }
}

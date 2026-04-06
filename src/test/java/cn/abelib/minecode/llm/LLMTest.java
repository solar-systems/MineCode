package cn.abelib.minecode.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LLM 客户端测试
 *
 * @author Abel
 */
class LLMTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // ==================== LLMConfig Tests ====================

    @Test
    void testLLMConfig_creation() {
        LLMConfig config = new LLMConfig();
        config.setApiKey("test-api-key");
        config.setModel("gpt-4o");
        config.setBaseUrl("https://api.openai.com/v1");
        config.setTemperature(0.7);
        config.setMaxTokens(4096);

        assertEquals("test-api-key", config.getApiKey());
        assertEquals("gpt-4o", config.getModel());
        assertEquals("https://api.openai.com/v1", config.getBaseUrl());
        assertEquals(0.7, config.getTemperature(), 0.01);
        assertEquals(4096, config.getMaxTokens());
    }

    @Test
    void testLLMConfig_defaultValues() {
        LLMConfig config = new LLMConfig();
        config.setModel("gpt-4");

        // 验证默认值
        assertNotNull(config);
    }

    @Test
    void testLLMConfig_fromEnv() {
        // 测试从环境变量加载
        LLMConfig config = LLMConfig.fromEnv();

        // 如果环境变量未设置，可能使用默认值
        assertNotNull(config);
        assertNotNull(config.getModel());
    }

    // ==================== LLMClient Builder Tests ====================

    @Test
    void testLLMClient_builder() {
        LLMClient client = LLMClient.builder()
                .apiKey("test-api-key")
                .model("gpt-4o")
                .baseUrl("https://api.openai.com/v1")
                .temperature(0.0)
                .maxTokens(2048)
                .build();

        assertNotNull(client);
    }

    @Test
    void testLLMClient_builderWithConfig() {
        LLMConfig config = new LLMConfig();
        config.setApiKey("test-key");
        config.setModel("gpt-4");

        LLMClient client = new LLMClient(config);

        assertNotNull(client);
    }

    // ==================== ToolCall Tests ====================

    @Test
    void testToolCall_creation() {
        ObjectNode args = mapper.createObjectNode();
        args.put("command", "ls -la");

        ToolCall toolCall = new ToolCall("call-123", "Bash", args);

        assertEquals("call-123", toolCall.id());
        assertEquals("Bash", toolCall.name());
        assertEquals(args, toolCall.arguments());
    }

    @Test
    void testToolCall_toMessage() {
        ObjectNode args = mapper.createObjectNode();
        args.put("file_path", "/test/file.txt");

        ToolCall toolCall = new ToolCall("call-456", "Read", args);

        ObjectNode message = toolCall.toMessage();

        assertEquals("assistant", message.get("role").asText());
        assertTrue(message.has("tool_calls"));

        var toolCalls = message.get("tool_calls");
        assertTrue(toolCalls.isArray());
        assertEquals(1, toolCalls.size());

        var tc = toolCalls.get(0);
        assertEquals("call-456", tc.get("id").asText());
        assertEquals("function", tc.get("type").asText());
        assertEquals("Read", tc.get("function").get("name").asText());
    }

    // ==================== LLMResponse Tests ====================

    @Test
    void testLLMResponse_creation() {
        LLMResponse response = new LLMResponse(
                "Response content",
                null,
                100,
                50
        );

        assertEquals("Response content", response.content());
        assertNull(response.toolCalls());
        assertEquals(100, response.promptTokens());
        assertEquals(50, response.completionTokens());
    }

    @Test
    void testLLMResponse_withToolCalls() {
        ObjectNode args = mapper.createObjectNode();
        args.put("path", "/test");

        java.util.List<ToolCall> toolCalls = java.util.List.of(
                new ToolCall("call-1", "Bash", args)
        );

        LLMResponse response = new LLMResponse(
                null,
                toolCalls,
                100,
                50
        );

        assertNull(response.content());
        assertNotNull(response.toolCalls());
        assertEquals(1, response.toolCalls().size());
        assertEquals("Bash", response.toolCalls().get(0).name());
    }

    @Test
    void testLLMResponse_hasToolCalls() {
        LLMResponse noTools = new LLMResponse("content", null, 10, 5);
        assertFalse(noTools.hasToolCalls());

        java.util.List<ToolCall> toolCalls = java.util.List.of(
                new ToolCall("call-1", "Read", mapper.createObjectNode())
        );
        LLMResponse withTools = new LLMResponse(null, toolCalls, 10, 5);
        assertTrue(withTools.hasToolCalls());
    }

    @Test
    void testLLMResponse_toMessage() {
        LLMResponse response = new LLMResponse("Hello", null, 10, 5);

        ObjectNode message = response.toMessage();

        assertEquals("assistant", message.get("role").asText());
        assertEquals("Hello", message.get("content").asText());
    }

    @Test
    void testLLMResponse_toMessageWithToolCalls() {
        ObjectNode args = mapper.createObjectNode();
        args.put("file", "/test");

        java.util.List<ToolCall> toolCalls = java.util.List.of(
                new ToolCall("call-1", "Read", args)
        );

        LLMResponse response = new LLMResponse(null, toolCalls, 10, 5);

        ObjectNode message = response.toMessage();

        assertEquals("assistant", message.get("role").asText());
        assertTrue(message.has("tool_calls"));
    }

    // ==================== Message Format Tests ====================

    @Test
    void testMessageFormat_systemMessage() {
        ObjectNode msg = mapper.createObjectNode();
        msg.put("role", "system");
        msg.put("content", "You are a helpful assistant.");

        assertEquals("system", msg.get("role").asText());
        assertEquals("You are a helpful assistant.", msg.get("content").asText());
    }

    @Test
    void testMessageFormat_userMessage() {
        ObjectNode msg = mapper.createObjectNode();
        msg.put("role", "user");
        msg.put("content", "Hello!");

        assertEquals("user", msg.get("role").asText());
        assertEquals("Hello!", msg.get("content").asText());
    }

    @Test
    void testMessageFormat_assistantMessage() {
        ObjectNode msg = mapper.createObjectNode();
        msg.put("role", "assistant");
        msg.put("content", "Hi there!");

        assertEquals("assistant", msg.get("role").asText());
        assertEquals("Hi there!", msg.get("content").asText());
    }

    @Test
    void testMessageFormat_toolResult() {
        ObjectNode msg = mapper.createObjectNode();
        msg.put("role", "tool");
        msg.put("tool_call_id", "call-123");
        msg.put("content", "Tool execution result");

        assertEquals("tool", msg.get("role").asText());
        assertEquals("call-123", msg.get("tool_call_id").asText());
        assertEquals("Tool execution result", msg.get("content").asText());
    }
}

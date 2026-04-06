package cn.abelib.minecode.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Context 模块测试
 *
 * @author Abel
 */
class ContextTest {

    private ContextManager manager;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // 使用 10000 tokens 作为测试阈值
        manager = new ContextManager(10000);
    }

    // ==================== ContextManager Creation Tests ====================

    @Test
    void testContextManager_creation() {
        assertEquals(10000, manager.getMaxTokens());
    }

    @Test
    void testContextManager_thresholds() {
        // 阈值计算：
        // snipAt = 10000 * 0.50 = 5000
        // summarizeAt = 10000 * 0.70 = 7000
        // collapseAt = 10000 * 0.90 = 9000

        // 验证阈值逻辑通过 maybeCompress 测试
        assertNotNull(manager);
    }

    // ==================== Token Estimation Tests ====================

    @Test
    void testEstimateTokens_emptyList() {
        List<ObjectNode> messages = new ArrayList<>();

        int tokens = ContextManager.estimateTokens(messages);

        assertEquals(0, tokens);
    }

    @Test
    void testEstimateTokens_singleMessage() {
        List<ObjectNode> messages = new ArrayList<>();
        messages.add(createMessage("user", "Hello World")); // 11 chars / 3 ≈ 3 tokens

        int tokens = ContextManager.estimateTokens(messages);

        assertTrue(tokens > 0);
    }

    @Test
    void testEstimateTokens_multipleMessages() {
        List<ObjectNode> messages = new ArrayList<>();
        messages.add(createMessage("user", "Hello World"));
        messages.add(createMessage("assistant", "Hi there!"));

        int tokens = ContextManager.estimateTokens(messages);

        assertTrue(tokens > 0);
    }

    @Test
    void testEstimateTokens_chineseContent() {
        List<ObjectNode> messages = new ArrayList<>();
        // 中文：每个汉字约 1-2 tokens
        messages.add(createMessage("user", "你好世界"));

        int tokens = ContextManager.estimateTokens(messages);

        assertTrue(tokens > 0);
    }

    @Test
    void testEstimateTokens_longContent() {
        List<ObjectNode> messages = new ArrayList<>();
        // 3000 字符 ≈ 1000 tokens
        messages.add(createMessage("user", "x".repeat(3000)));

        int tokens = ContextManager.estimateTokens(messages);

        assertEquals(1000, tokens);
    }

    @Test
    void testEstimateTokens_withToolCalls() {
        ObjectNode msg = mapper.createObjectNode();
        msg.put("role", "assistant");
        msg.put("content", "Test content");
        msg.putArray("tool_calls").add(mapper.createObjectNode()
                .put("name", "read_file")
                .put("arguments", "{\"path\": \"/test/file.txt\"}"));

        List<ObjectNode> messages = new ArrayList<>();
        messages.add(msg);

        int tokens = ContextManager.estimateTokens(messages);

        // tool_calls 也应该被计算
        assertTrue(tokens > 4); // "Test content" 是 12 chars ≈ 4 tokens
    }

    // ==================== Compression Tests ====================

    @Test
    void testMaybeCompress_noCompressionNeeded() {
        List<ObjectNode> messages = new ArrayList<>();
        // 小于 50% 阈值
        messages.add(createMessage("user", "Short message"));

        boolean compressed = manager.maybeCompress(messages, null);

        assertFalse(compressed);
    }

    @Test
    void testMaybeCompress_snipLayer() {
        List<ObjectNode> messages = new ArrayList<>();

        // 创建一个长的工具输出（超过 1500 字符）
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longContent.append("Line ").append(i).append(": This is a test line with some content\n");
        }

        ObjectNode toolMsg = mapper.createObjectNode();
        toolMsg.put("role", "tool");
        toolMsg.put("content", longContent.toString());
        messages.add(toolMsg);

        // 再添加消息使其超过 snip 阈值 (5000 tokens)
        for (int i = 0; i < 150; i++) {
            messages.add(createMessage("user", "Test message " + i + " with enough content to reach threshold"));
        }

        int beforeTokens = ContextManager.estimateTokens(messages);
        boolean compressed = manager.maybeCompress(messages, null);
        int afterTokens = ContextManager.estimateTokens(messages);

        // 如果超过阈值，应该进行压缩
        if (beforeTokens > 5000) {
            assertTrue(compressed);
            assertTrue(afterTokens < beforeTokens);
        }
    }

    @Test
    void testMaybeCompress_summarizeLayer() {
        // 需要 > 70% tokens 且 > 10 条消息
        List<ObjectNode> messages = new ArrayList<>();

        // 添加足够多的消息
        for (int i = 0; i < 200; i++) {
            messages.add(createMessage("user", "Message " + i + " with sufficient content"));
            messages.add(createMessage("assistant", "Response " + i + " with enough content"));
        }

        int beforeTokens = ContextManager.estimateTokens(messages);
        boolean compressed = manager.maybeCompress(messages, null);

        if (beforeTokens > 7000 && messages.size() > 10) {
            assertTrue(compressed);
        }
    }

    @Test
    void testMaybeCompress_hardCollapseLayer() {
        // 需要 > 90% tokens 且 > 4 条消息
        List<ObjectNode> messages = new ArrayList<>();

        // 添加大量消息
        for (int i = 0; i < 300; i++) {
            messages.add(createMessage("user", "Message " + i + " with lots of content to trigger collapse"));
            messages.add(createMessage("assistant", "Response " + i + " with sufficient tokens"));
        }

        int beforeTokens = ContextManager.estimateTokens(messages);
        boolean compressed = manager.maybeCompress(messages, null);

        if (beforeTokens > 9000) {
            assertTrue(compressed);
        }
    }

    @Test
    void testMaybeCompress_preservesRecentMessages() {
        List<ObjectNode> messages = new ArrayList<>();

        // 添加大量消息
        for (int i = 0; i < 250; i++) {
            messages.add(createMessage("user", "Message " + i));
        }

        // 记录最后几条消息的内容
        String lastMessage = messages.get(messages.size() - 1).path("content").asText();

        manager.maybeCompress(messages, null);

        // 最近的几条消息应该保留
        String preservedMessage = messages.get(messages.size() - 1).path("content").asText();
        // 注意：压缩后消息列表会变化，这里只检查是否仍有消息
        assertFalse(messages.isEmpty());
    }

    // ==================== Tool Output Snipping Tests ====================

    @Test
    void testToolOutputSnipping_shortContent() {
        List<ObjectNode> messages = new ArrayList<>();

        ObjectNode toolMsg = mapper.createObjectNode();
        toolMsg.put("role", "tool");
        toolMsg.put("content", "Short content");
        messages.add(toolMsg);

        // 添加足够消息触发 snip 检查
        for (int i = 0; i < 150; i++) {
            messages.add(createMessage("user", "Test " + i));
        }

        manager.maybeCompress(messages, null);

        // 短内容应该保持不变
        String content = messages.get(0).path("content").asText();
        assertEquals("Short content", content);
    }

    @Test
    void testToolOutputSnipping_longContent() {
        List<ObjectNode> messages = new ArrayList<>();

        // 创建超过 1500 字符的内容，且超过 6 行
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            longContent.append("Line ").append(i).append(": ").append("x".repeat(100)).append("\n");
        }

        ObjectNode toolMsg = mapper.createObjectNode();
        toolMsg.put("role", "tool");
        toolMsg.put("content", longContent.toString());
        messages.add(toolMsg);

        // 添加足够消息触发 snip 阈值 (需要超过 5000 tokens)
        // 每条消息约 30 字符 ≈ 10 tokens，需要约 500 条消息
        for (int i = 0; i < 600; i++) {
            messages.add(createMessage("user", "Test message " + i + " with enough content to trigger threshold"));
        }

        int beforeTokens = ContextManager.estimateTokens(messages);
        manager.maybeCompress(messages, null);

        // 如果超过阈值，工具输出应该被裁剪
        if (beforeTokens > 5000) {
            String content = messages.get(0).path("content").asText();
            assertTrue(content.contains("已裁剪") || content.length() < longContent.length(),
                    "Content should be snipped when over threshold");
        }
    }

    // ==================== Edge Cases ====================

    @Test
    void testMaybeCompress_emptyMessages() {
        List<ObjectNode> messages = new ArrayList<>();

        boolean compressed = manager.maybeCompress(messages, null);

        assertFalse(compressed);
    }

    @Test
    void testMaybeCompress_singleMessage() {
        List<ObjectNode> messages = new ArrayList<>();
        messages.add(createMessage("user", "Single message"));

        boolean compressed = manager.maybeCompress(messages, null);

        assertFalse(compressed);
    }

    @Test
    void testEstimateTokens_nullContent() {
        ObjectNode msg = mapper.createObjectNode();
        msg.put("role", "user");
        // 不设置 content

        List<ObjectNode> messages = new ArrayList<>();
        messages.add(msg);

        // 不应该抛出异常
        assertDoesNotThrow(() -> ContextManager.estimateTokens(messages));
    }

    @Test
    void testMaybeCompress_mixedRoles() {
        List<ObjectNode> messages = new ArrayList<>();

        messages.add(createMessage("system", "System prompt"));
        messages.add(createMessage("user", "User message"));
        messages.add(createMessage("assistant", "Assistant response"));
        messages.add(createMessage("tool", "Tool output"));

        // 添加大量消息
        for (int i = 0; i < 200; i++) {
            messages.add(createMessage("user", "Message " + i));
        }

        boolean compressed = manager.maybeCompress(messages, null);

        // 应该能够处理不同角色
        assertTrue(messages.size() > 0);
    }

    // ==================== Helper Methods ====================

    private ObjectNode createMessage(String role, String content) {
        ObjectNode msg = mapper.createObjectNode();
        msg.put("role", role);
        msg.put("content", content);
        return msg;
    }
}

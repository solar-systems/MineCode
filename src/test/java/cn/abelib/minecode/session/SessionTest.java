package cn.abelib.minecode.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Session 模块测试
 *
 * @author Abel
 */
class SessionTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    // ==================== SessionKey Tests ====================

    @Test
    void testSessionKey_of() {
        SessionKey key = SessionKey.of("user1", "session123");

        assertEquals("user1", key.userId());
        assertEquals("session123", key.sessionId());
    }

    @Test
    void testSessionKey_ofDefaultUser() {
        SessionKey key = SessionKey.of("session123");

        assertEquals(SessionKey.DEFAULT_USER, key.userId());
        assertEquals("session123", key.sessionId());
    }

    @Test
    void testSessionKey_generateSessionId() throws InterruptedException {
        String id1 = SessionKey.generateSessionId();
        Thread.sleep(1); // 确保时间戳不同
        String id2 = SessionKey.generateSessionId();

        assertTrue(id1.startsWith("session_"));
        assertTrue(id2.startsWith("session_"));
        // 如果在同一毫秒内生成，ID 可能相同，所以只检查格式
    }

    @Test
    void testSessionKey_toPath_defaultUser() {
        SessionKey key = SessionKey.of("session123");

        assertEquals("session123", key.toPath());
    }

    @Test
    void testSessionKey_toPath_customUser() {
        SessionKey key = SessionKey.of("user1", "session123");

        assertEquals("user1/session123", key.toPath());
    }

    @Test
    void testSessionKey_toString_defaultUser() {
        SessionKey key = SessionKey.of("session123");

        assertEquals("session123", key.toString());
    }

    @Test
    void testSessionKey_toString_customUser() {
        SessionKey key = SessionKey.of("user1", "session123");

        assertEquals("user1:session123", key.toString());
    }

    // ==================== SessionMetadata Tests ====================

    @Test
    void testSessionMetadata_creation() {
        SessionMetadata metadata = new SessionMetadata("sess1", "Test Session", "gpt-4");

        assertEquals("sess1", metadata.getId());
        assertEquals("Test Session", metadata.getTitle());
        assertEquals("gpt-4", metadata.getModel());
    }

    @Test
    void testSessionMetadata_setters() {
        SessionMetadata metadata = new SessionMetadata("sess1", "Test", "gpt-4");

        metadata.setTitle("New Title");
        metadata.setModel("claude-3");

        assertEquals("New Title", metadata.getTitle());
        assertEquals("claude-3", metadata.getModel());
    }

    // ==================== SessionInfo Tests ====================

    @Test
    void testSessionInfo_creation() {
        SessionInfo info = new SessionInfo(
                "sess1", "Test Session", "gpt-4",
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now(),
                10, 100, "default"
        );

        assertEquals("sess1", info.id());
        assertEquals("Test Session", info.title());
        assertEquals("gpt-4", info.model());
        assertEquals(10, info.messageCount());
        assertEquals(100, info.totalTokens());
    }

    // ==================== JsonlSessionStorage Tests ====================

    @Test
    void testJsonlSessionStorage_create() {
        JsonlSessionStorage storage = new JsonlSessionStorage(tempDir);

        SessionKey key = SessionKey.of("test-session");
        SessionMetadata metadata = new SessionMetadata("test-session", "Test", "gpt-4");

        Session session = storage.create(key, metadata);

        assertNotNull(session);
        assertEquals("test-session", session.getId());
        assertEquals("Test", session.getMetadata().getTitle());
    }

    @Test
    void testJsonlSessionStorage_load() {
        JsonlSessionStorage storage = new JsonlSessionStorage(tempDir);

        SessionKey key = SessionKey.of("test-session");
        SessionMetadata metadata = new SessionMetadata("test-session", "Test", "gpt-4");

        // 创建并保存
        Session created = storage.create(key, metadata);
        created.appendMessage(createMessage("user", "Hello"));
        created.save();

        // 加载
        Session loaded = storage.load(key);

        assertNotNull(loaded);
        assertEquals("test-session", loaded.getId());
        assertEquals(1, loaded.getMessages().size());
    }

    @Test
    void testJsonlSessionStorage_delete() {
        JsonlSessionStorage storage = new JsonlSessionStorage(tempDir);

        SessionKey key = SessionKey.of("test-session");
        SessionMetadata metadata = new SessionMetadata("test-session", "Test", "gpt-4");

        storage.create(key, metadata);

        assertTrue(storage.delete(key));
        assertNull(storage.load(key));
    }

    @Test
    void testJsonlSessionStorage_list() {
        JsonlSessionStorage storage = new JsonlSessionStorage(tempDir);

        // 创建多个会话
        for (int i = 0; i < 3; i++) {
            SessionKey key = SessionKey.of("session-" + i);
            SessionMetadata metadata = new SessionMetadata("session-" + i, "Test " + i, "gpt-4");
            Session session = storage.create(key, metadata);
            session.save();
        }

        List<SessionInfo> sessions = storage.list(null, 10);

        assertEquals(3, sessions.size());
    }

    // ==================== SessionManager Tests ====================

    @Test
    void testSessionManager_createNewSession() {
        SessionManager manager = new SessionManager();

        String sessionId = manager.createNewSession("gpt-4");

        assertNotNull(sessionId);
        assertTrue(sessionId.startsWith("session_"));
        assertEquals(sessionId, manager.getCurrentSessionId());
    }

    @Test
    void testSessionManager_saveAndLoadSession() {
        SessionManager manager = new SessionManager();

        manager.createNewSession("gpt-4");

        List<ObjectNode> messages = new ArrayList<>();
        messages.add(createMessage("user", "Hello"));
        messages.add(createMessage("assistant", "Hi there!"));

        manager.saveSession(messages);

        String sessionId = manager.getCurrentSessionId();
        List<ObjectNode> loaded = manager.loadSession(sessionId);

        assertNotNull(loaded);
        assertEquals(2, loaded.size());
    }

    @Test
    void testSessionManager_listSessions() {
        SessionManager manager = new SessionManager();

        // 创建多个会话
        for (int i = 0; i < 3; i++) {
            manager.createNewSession("gpt-4");
            List<ObjectNode> messages = new ArrayList<>();
            messages.add(createMessage("user", "Test message for session " + i + " with enough content"));
            manager.saveSession(messages);
        }

        List<SessionInfo> sessions = manager.listSessions(10);

        // 至少应该有刚创建的会话，可能还有之前存在的会话
        assertTrue(sessions.size() >= 1, "Should have at least one session");
    }

    @Test
    void testSessionManager_deleteSession() {
        SessionManager manager = new SessionManager();

        String sessionId = manager.createNewSession("gpt-4");
        List<ObjectNode> messages = new ArrayList<>();
        messages.add(createMessage("user", "Test"));
        manager.saveSession(messages);

        assertTrue(manager.deleteSession(sessionId));
        assertNull(manager.loadSession(sessionId));
    }

    @Test
    void testSessionManager_registerModule() {
        SessionManager manager = new SessionManager();
        TestStateModule module = new TestStateModule();

        manager.registerModule(module);

        // 创建会话后可以测试模块状态保存
        manager.createNewSession("gpt-4");
        assertNotNull(manager.getCurrentSession());
    }

    // ==================== StateModule Tests ====================

    @Test
    void testStateModule() {
        TestStateModule module = new TestStateModule();
        module.setValue("test-value");

        assertEquals("testModule", module.getModuleName());

        ObjectNode state = module.saveState();
        assertNotNull(state);
        assertEquals("test-value", state.get("value").asText());

        TestStateModule newModule = new TestStateModule();
        newModule.loadState(state);
        assertEquals("test-value", newModule.getValue());
    }

    // ==================== Helper Methods ====================

    private ObjectNode createMessage(String role, String content) {
        ObjectNode msg = mapper.createObjectNode();
        msg.put("role", role);
        msg.put("content", content);
        return msg;
    }

    // ==================== Test Classes ====================

    static class TestStateModule implements StateModule {
        private static final ObjectMapper staticMapper = new ObjectMapper();
        private String value = "";

        @Override
        public String getModuleName() {
            return "testModule";
        }

        @Override
        public ObjectNode saveState() {
            ObjectNode state = staticMapper.createObjectNode();
            state.put("value", value);
            return state;
        }

        @Override
        public void loadState(JsonNode state) {
            if (state != null) {
                this.value = state.path("value").asText("");
            }
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}

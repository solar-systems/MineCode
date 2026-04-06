package cn.abelib.minecode.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 会话管理器 - 对话持久化与恢复
 *
 * <p>功能：
 * <ul>
 *   <li>保存对话历史到本地文件</li>
 *   <li>从文件恢复对话</li>
 *   <li>会话列表管理</li>
 *   <li>支持增量追加保存（新）</li>
 *   <li>支持多组件状态保存（新）</li>
 * </ul>
 *
 * <p>存储位置：~/.minecode/sessions/
 *
 * @author Abel
 */
public class SessionManager {
    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path sessionsDir;
    private final SessionStorage storage;
    private final List<StateModule> modules = new ArrayList<>();

    private String currentSessionId;
    private String model;
    private Session currentSession;

    public SessionManager() {
        this.sessionsDir = Path.of(System.getProperty("user.home"), ".minecode", "sessions");
        ensureSessionsDir();
        // 使用 JSONL 存储支持增量追加
        this.storage = new JsonlSessionStorage(sessionsDir);
    }

    /**
     * 注册状态模块
     *
     * @param module 状态模块
     */
    public void registerModule(StateModule module) {
        modules.add(module);
    }

    /**
     * 注销状态模块
     *
     * @param module 状态模块
     */
    public void unregisterModule(StateModule module) {
        modules.remove(module);
    }

    /**
     * 创建新会话
     */
    public String createNewSession(String model) {
        this.currentSessionId = SessionKey.generateSessionId();
        this.model = model;

        SessionKey key = SessionKey.of(currentSessionId);
        SessionMetadata metadata = new SessionMetadata(currentSessionId, "New Session", model);
        this.currentSession = storage.create(key, metadata);

        return currentSessionId;
    }

    /**
     * 保存对话历史（全量保存，向后兼容）
     *
     * @param messages 消息列表
     * @return 保存的文件路径
     */
    public Path saveSession(List<ObjectNode> messages) {
        if (currentSessionId == null) {
            currentSessionId = SessionKey.generateSessionId();
        }

        // 如果有当前会话，使用增量保存
        if (currentSession != null) {
            return saveWithIncremental(messages);
        }

        // 兼容旧模式：全量保存
        return saveFullSession(messages);
    }

    /**
     * 增量保存消息
     *
     * @param messages 消息列表
     * @return 文件路径
     */
    private Path saveWithIncremental(List<ObjectNode> messages) {
        try {
            // 更新标题
            String title = extractTitle(messages);
            currentSession.getMetadata().setTitle(title);

            // 同步消息（增量追加）
            List<JsonNode> existingMessages = currentSession.getMessages();
            int startIndex = existingMessages.size();

            for (int i = startIndex; i < messages.size(); i++) {
                currentSession.appendMessage(messages.get(i));
            }

            // 保存模块状态
            saveModuleStates();

            currentSession.save();

            Path file = sessionsDir.resolve(currentSessionId + ".jsonl");
            log.debug("Session saved incrementally: {}", file);
            return file;

        } catch (Exception e) {
            log.error("Failed to save session incrementally", e);
            return null;
        }
    }

    /**
     * 全量保存会话（旧格式兼容）
     */
    private Path saveFullSession(List<ObjectNode> messages) {
        try {
            ObjectNode session = mapper.createObjectNode();
            session.put("id", currentSessionId);
            session.put("model", model != null ? model : "unknown");
            session.put("saved_at", LocalDateTime.now().format(FORMATTER));

            String title = extractTitle(messages);
            session.put("title", title);

            ArrayNode messagesArray = session.putArray("messages");
            messages.forEach(messagesArray::add);

            Path sessionFile = sessionsDir.resolve(currentSessionId + ".json");
            Files.writeString(sessionFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(session));

            log.debug("Session saved: {}", sessionFile);
            return sessionFile;

        } catch (IOException e) {
            log.error("Failed to save session", e);
            return null;
        }
    }

    /**
     * 加载会话
     *
     * @param sessionId 会话 ID
     * @return 消息列表，失败返回 null
     */
    public List<ObjectNode> loadSession(String sessionId) {
        // 先尝试 JSONL 格式
        Path jsonlFile = sessionsDir.resolve(sessionId + ".jsonl");
        if (Files.exists(jsonlFile)) {
            return loadJsonlSession(sessionId);
        }

        // 兼容 JSON 格式
        Path jsonFile = sessionsDir.resolve(sessionId + ".json");
        if (Files.exists(jsonFile)) {
            return loadJsonSession(sessionId);
        }

        log.warn("Session file not found: {}", sessionId);
        return null;
    }

    /**
     * 加载 JSONL 格式会话
     */
    private List<ObjectNode> loadJsonlSession(String sessionId) {
        SessionKey key = SessionKey.of(sessionId);
        Session session = storage.load(key);

        if (session == null) {
            return null;
        }

        this.currentSessionId = sessionId;
        this.currentSession = session;
        this.model = session.getMetadata().getModel();

        // 加载模块状态
        loadModuleStates(session);

        List<ObjectNode> messages = new ArrayList<>();
        for (JsonNode msg : session.getMessages()) {
            if (msg.isObject()) {
                messages.add((ObjectNode) msg);
            }
        }

        log.info("Session loaded: {} ({} messages)", sessionId, messages.size());
        return messages;
    }

    /**
     * 加载 JSON 格式会话（旧格式兼容）
     */
    private List<ObjectNode> loadJsonSession(String sessionId) {
        Path sessionFile = sessionsDir.resolve(sessionId + ".json");

        try {
            ObjectNode session = (ObjectNode) mapper.readTree(Files.readString(sessionFile));
            this.currentSessionId = sessionId;
            this.model = session.path("model").asText("unknown");

            List<ObjectNode> messages = new ArrayList<>();
            ArrayNode messagesArray = (ArrayNode) session.path("messages");
            messagesArray.forEach(node -> messages.add((ObjectNode) node));

            // 迁移到 JSONL 格式
            migrateToJsonl(sessionId, messages, session);

            log.info("Session loaded (migrated): {} ({} messages)", sessionId, messages.size());
            return messages;

        } catch (IOException e) {
            log.error("Failed to load session", e);
            return null;
        }
    }

    /**
     * 迁移 JSON 格式到 JSONL 格式
     */
    private void migrateToJsonl(String sessionId, List<ObjectNode> messages, ObjectNode oldSession) {
        try {
            SessionKey key = SessionKey.of(sessionId);
            SessionMetadata metadata = new SessionMetadata(
                    sessionId,
                    oldSession.path("title").asText("Untitled"),
                    oldSession.path("model").asText("unknown")
            );

            Session newSession = storage.create(key, metadata);
            for (ObjectNode msg : messages) {
                newSession.appendMessage(msg);
            }
            newSession.save();

            // 删除旧文件
            Files.deleteIfExists(sessionsDir.resolve(sessionId + ".json"));

            this.currentSession = newSession;
            log.info("Session migrated to JSONL: {}", sessionId);

        } catch (Exception e) {
            log.warn("Failed to migrate session to JSONL: {}", sessionId, e);
        }
    }

    /**
     * 列出所有会话
     *
     * @param limit 最大数量
     * @return 会话信息列表
     */
    public List<SessionInfo> listSessions(int limit) {
        List<SessionInfo> sessions = new ArrayList<>();

        try (Stream<Path> stream = Files.list(sessionsDir)) {
            stream.filter(p -> p.toString().endsWith(".jsonl") || p.toString().endsWith(".json"))
                    .sorted((a, b) -> {
                        try {
                            return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .limit(limit)
                    .forEach(p -> {
                        try {
                            SessionInfo info = readSessionInfo(p);
                            if (info != null) {
                                sessions.add(info);
                            }
                        } catch (Exception e) {
                            log.warn("Failed to read session: {}", p);
                        }
                    });

        } catch (IOException e) {
            log.error("Failed to list sessions", e);
        }

        return sessions;
    }

    /**
     * 删除会话
     *
     * @param sessionId 会话 ID
     * @return 是否成功
     */
    public boolean deleteSession(String sessionId) {
        SessionKey key = SessionKey.of(sessionId);

        // 删除 JSONL 文件
        boolean deleted = storage.delete(key);

        // 也删除可能存在的 JSON 文件
        try {
            Files.deleteIfExists(sessionsDir.resolve(sessionId + ".json"));
        } catch (IOException e) {
            // ignore
        }

        if (deleted) {
            log.info("Session deleted: {}", sessionId);
        }
        return deleted;
    }

    /**
     * 自动保存（带防抖）
     */
    public void autoSave(List<ObjectNode> messages) {
        if (!messages.isEmpty()) {
            saveSession(messages);
        }
    }

    /**
     * 获取当前会话 ID
     */
    public String getCurrentSessionId() {
        return currentSessionId;
    }

    /**
     * 设置当前模型
     */
    public void setModel(String model) {
        this.model = model;
        if (currentSession != null) {
            currentSession.getMetadata().setModel(model);
        }
    }

    /**
     * 获取当前会话（新接口）
     */
    public Optional<Session> getCurrentSession() {
        return Optional.ofNullable(currentSession);
    }

    /**
     * 获取存储实例（新接口）
     */
    public SessionStorage getStorage() {
        return storage;
    }

    // ==================== Private Methods ====================

    private void ensureSessionsDir() {
        try {
            Files.createDirectories(sessionsDir);
        } catch (IOException e) {
            log.error("Failed to create sessions directory", e);
        }
    }

    private String extractTitle(List<ObjectNode> messages) {
        for (ObjectNode msg : messages) {
            if ("user".equals(msg.path("role").asText())) {
                String content = msg.path("content").asText("");
                return content.length() > 50 ? content.substring(0, 50) + "..." : content;
            }
        }
        return "Untitled Session";
    }

    private SessionInfo readSessionInfo(Path file) throws IOException {
        String filename = file.getFileName().toString();
        String sessionId = filename.replace(".jsonl", "").replace(".json", "");

        if (filename.endsWith(".jsonl")) {
            // JSONL 格式
            return storage.list(null, 1).stream()
                    .filter(s -> s.id().equals(sessionId))
                    .findFirst()
                    .orElse(null);
        } else {
            // JSON 格式（旧格式）
            ObjectNode session = (ObjectNode) mapper.readTree(Files.readString(file));
            return new SessionInfo(
                    session.path("id").asText(),
                    session.path("title").asText("Untitled"),
                    session.path("model").asText("unknown"),
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    session.path("messages").size(),
                    0,
                    SessionKey.DEFAULT_USER
            );
        }
    }

    private void saveModuleStates() {
        if (currentSession == null) return;

        for (StateModule module : modules) {
            try {
                JsonNode state = module.saveState();
                if (state != null) {
                    currentSession.setModuleState(module.getModuleName(), state);
                }
            } catch (Exception e) {
                log.warn("Failed to save module state: {}", module.getModuleName(), e);
            }
        }
    }

    private void loadModuleStates(Session session) {
        for (StateModule module : modules) {
            try {
                JsonNode state = session.getModuleState(module.getModuleName());
                if (state != null) {
                    module.loadState(state);
                }
            } catch (Exception e) {
                log.warn("Failed to load module state: {}", module.getModuleName(), e);
            }
        }
    }
}

package cn.abelib.minecode.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * JSONL 会话存储 - 支持增量追加
 *
 * <p>使用 JSONL 格式存储会话：
 * <ul>
 *   <li>第一行：元数据（JSON 对象）</li>
 *   <li>后续行：消息（每行一条）</li>
 * </ul>
 *
 * <p>优势：
 * <ul>
 *   <li>增量追加：只追加新消息，避免全量重写</li>
 *   <li>流式加载：按需加载消息，节省内存</li>
 *   <li>高性能：适合长对话场景</li>
 * </ul>
 *
 * @author Abel
 */
public class JsonlSessionStorage implements SessionStorage {

    private static final Logger log = LoggerFactory.getLogger(JsonlSessionStorage.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path sessionsDir;

    public JsonlSessionStorage() {
        this(Path.of(System.getProperty("user.home"), ".minecode", "sessions"));
    }

    public JsonlSessionStorage(Path sessionsDir) {
        this.sessionsDir = sessionsDir;
        ensureSessionsDir();
    }

    @Override
    public Session create(SessionKey key, SessionMetadata metadata) {
        metadata.setId(key.sessionId());
        metadata.setCreatedAt(LocalDateTime.now());
        metadata.setUpdatedAt(LocalDateTime.now());

        Path file = getSessionFile(key);

        try {
            // 写入元数据行
            ObjectNode metadataNode = metadataToJson(metadata);
            metadataNode.put("__type__", "metadata");

            Files.writeString(file, mapper.writeValueAsString(metadataNode) + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            log.debug("Session created: {}", key);
            return new SessionImpl(key, metadata, this);

        } catch (IOException e) {
            log.error("Failed to create session: {}", key, e);
            throw new RuntimeException("Failed to create session", e);
        }
    }

    @Override
    public Session load(SessionKey key) {
        Path file = getSessionFile(key);

        if (!Files.exists(file)) {
            log.warn("Session file not found: {}", file);
            return null;
        }

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            // 第一行是元数据
            String metadataLine = reader.readLine();
            if (metadataLine == null) {
                log.warn("Empty session file: {}", file);
                return null;
            }

            JsonNode metadataNode = mapper.readTree(metadataLine);
            SessionMetadata metadata = jsonToMetadata((ObjectNode) metadataNode);

            // 加载消息
            List<JsonNode> messages = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    JsonNode msgNode = mapper.readTree(line);
                    if ("message".equals(msgNode.path("__type__").asText())) {
                        ((ObjectNode) msgNode).remove("__type__");
                        messages.add(msgNode);
                    }
                }
            }

            metadata.setMessageCount(messages.size());

            return new SessionImpl(key, metadata, messages, this);

        } catch (IOException e) {
            log.error("Failed to load session: {}", key, e);
            return null;
        }
    }

    @Override
    public void save(Session session) {
        // JSONL 格式下，save 主要用于更新元数据
        // 消息通过 appendMessage 增量追加
        try {
            Path file = getSessionFile(session.getKey());
            SessionMetadata metadata = session.getMetadata();
            metadata.setUpdatedAt(LocalDateTime.now());
            metadata.setMessageCount(session.getMessageCount());

            // 重写整个文件（用于更新元数据）
            rewriteSession(session);

            log.debug("Session saved: {}", session.getKey());

        } catch (IOException e) {
            log.error("Failed to save session: {}", session.getKey(), e);
            throw new RuntimeException("Failed to save session", e);
        }
    }

    @Override
    public boolean delete(SessionKey key) {
        Path file = getSessionFile(key);

        try {
            boolean deleted = Files.deleteIfExists(file);
            if (deleted) {
                log.info("Session deleted: {}", key);
            }
            return deleted;
        } catch (IOException e) {
            log.error("Failed to delete session: {}", key, e);
            return false;
        }
    }

    @Override
    public List<SessionInfo> list(String userId, int limit) {
        List<SessionInfo> sessions = new ArrayList<>();

        try (Stream<Path> stream = Files.list(sessionsDir)) {
            stream.filter(p -> p.toString().endsWith(".jsonl"))
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
                            SessionInfo info = readSessionInfo(p, userId);
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

    @Override
    public boolean exists(SessionKey key) {
        return Files.exists(getSessionFile(key));
    }

    /**
     * 增量追加消息
     *
     * @param key     会话键
     * @param message 消息 JSON
     */
    public void appendMessage(SessionKey key, JsonNode message) {
        Path file = getSessionFile(key);

        try {
            // 创建消息行
            ObjectNode msgLine = message.deepCopy();
            msgLine.put("__type__", "message");

            Files.writeString(file, mapper.writeValueAsString(msgLine) + "\n",
                    StandardOpenOption.APPEND, StandardOpenOption.CREATE);

            log.debug("Message appended to session: {}", key);

        } catch (IOException e) {
            log.error("Failed to append message: {}", key, e);
            throw new RuntimeException("Failed to append message", e);
        }
    }

    /**
     * 流式读取消息
     *
     * @param key 会话键
     * @return 消息流
     */
    public Stream<JsonNode> streamMessages(SessionKey key) {
        Path file = getSessionFile(key);

        if (!Files.exists(file)) {
            return Stream.empty();
        }

        try {
            BufferedReader reader = Files.newBufferedReader(file);
            // 跳过元数据行
            reader.readLine();

            return reader.lines()
                    .filter(line -> !line.isEmpty())
                    .map(line -> {
                        try {
                            JsonNode node = mapper.readTree(line);
                            if ("message".equals(node.path("__type__").asText())) {
                                ((ObjectNode) node).remove("__type__");
                            }
                            return node;
                        } catch (IOException e) {
                            log.warn("Failed to parse message line: {}", line);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .onClose(() -> {
                        try {
                            reader.close();
                        } catch (IOException e) {
                            log.warn("Failed to close reader", e);
                        }
                    });

        } catch (IOException e) {
            log.error("Failed to stream messages: {}", key, e);
            return Stream.empty();
        }
    }

    /**
     * 读取最后 N 条消息
     *
     * @param key  会话键
     * @param last 最后 N 条
     * @return 消息列表
     */
    public List<JsonNode> readLastMessages(SessionKey key, int last) {
        List<JsonNode> messages = new ArrayList<>();

        try (Stream<JsonNode> stream = streamMessages(key)) {
            List<JsonNode> all = stream.toList();
            int start = Math.max(0, all.size() - last);
            return all.subList(start, all.size());

        } catch (Exception e) {
            log.error("Failed to read last messages: {}", key, e);
            return messages;
        }
    }

    // ==================== Private Methods ====================

    private Path getSessionFile(SessionKey key) {
        return sessionsDir.resolve(key.sessionId() + ".jsonl");
    }

    private void ensureSessionsDir() {
        try {
            Files.createDirectories(sessionsDir);
        } catch (IOException e) {
            log.error("Failed to create sessions directory", e);
        }
    }

    private ObjectNode metadataToJson(SessionMetadata metadata) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", metadata.getId());
        node.put("title", metadata.getTitle());
        node.put("model", metadata.getModel());
        node.put("createdAt", metadata.getCreatedAt() != null
                ? metadata.getCreatedAt().format(FORMATTER) : null);
        node.put("updatedAt", metadata.getUpdatedAt() != null
                ? metadata.getUpdatedAt().format(FORMATTER) : null);
        node.put("messageCount", metadata.getMessageCount());
        node.put("totalTokens", metadata.getTotalTokens());
        node.put("parentId", metadata.getParentId());

        if (metadata.getTags() != null && !metadata.getTags().isEmpty()) {
            ObjectNode tagsNode = node.putObject("tags");
            metadata.getTags().forEach(tagsNode::put);
        }

        return node;
    }

    private SessionMetadata jsonToMetadata(ObjectNode node) {
        SessionMetadata metadata = new SessionMetadata();
        metadata.setId(node.path("id").asText());
        metadata.setTitle(node.path("title").asText("Untitled"));
        metadata.setModel(node.path("model").asText("unknown"));

        String createdAtStr = node.path("createdAt").asText(null);
        if (createdAtStr != null) {
            metadata.setCreatedAt(LocalDateTime.parse(createdAtStr, FORMATTER));
        }

        String updatedAtStr = node.path("updatedAt").asText(null);
        if (updatedAtStr != null) {
            metadata.setUpdatedAt(LocalDateTime.parse(updatedAtStr, FORMATTER));
        }

        metadata.setMessageCount(node.path("messageCount").asInt(0));
        metadata.setTotalTokens(node.path("totalTokens").asLong(0));
        metadata.setParentId(node.path("parentId").asText(null));

        JsonNode tagsNode = node.path("tags");
        if (tagsNode.isObject()) {
            Map<String, String> tags = new HashMap<>();
            tagsNode.fields().forEachRemaining(entry ->
                    tags.put(entry.getKey(), entry.getValue().asText()));
            metadata.setTags(tags);
        }

        return metadata;
    }

    private SessionInfo readSessionInfo(Path file, String userId) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String metadataLine = reader.readLine();
            if (metadataLine == null) {
                return null;
            }

            JsonNode node = mapper.readTree(metadataLine);
            SessionMetadata metadata = jsonToMetadata((ObjectNode) node);

            // 计算消息数
            int count = 0;
            while (reader.readLine() != null) {
                count++;
            }
            metadata.setMessageCount(count);

            return SessionInfo.from(metadata, userId != null ? userId : SessionKey.DEFAULT_USER);
        }
    }

    private void rewriteSession(Session session) throws IOException {
        Path file = getSessionFile(session.getKey());

        // 写入元数据
        ObjectNode metadataNode = metadataToJson(session.getMetadata());
        metadataNode.put("__type__", "metadata");

        StringBuilder content = new StringBuilder();
        content.append(mapper.writeValueAsString(metadataNode)).append("\n");

        // 写入消息
        for (JsonNode msg : session.getMessages()) {
            ObjectNode msgLine = msg.deepCopy();
            msgLine.put("__type__", "message");
            content.append(mapper.writeValueAsString(msgLine)).append("\n");
        }

        Files.writeString(file, content.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    // ==================== Session Implementation ====================

    /**
     * Session 接口的内部实现
     */
    private static class SessionImpl implements Session {
        private final SessionKey key;
        private SessionMetadata metadata;
        private final List<JsonNode> messages;
        private final Map<String, JsonNode> moduleStates;
        private final JsonlSessionStorage storage;

        SessionImpl(SessionKey key, SessionMetadata metadata, JsonlSessionStorage storage) {
            this(key, metadata, new ArrayList<>(), storage);
        }

        SessionImpl(SessionKey key, SessionMetadata metadata, List<JsonNode> messages, JsonlSessionStorage storage) {
            this.key = key;
            this.metadata = metadata;
            this.messages = new ArrayList<>(messages);
            this.moduleStates = new HashMap<>();
            this.storage = storage;
        }

        @Override
        public String getId() {
            return key.sessionId();
        }

        @Override
        public SessionKey getKey() {
            return key;
        }

        @Override
        public SessionMetadata getMetadata() {
            return metadata;
        }

        @Override
        public List<JsonNode> getMessages() {
            return Collections.unmodifiableList(messages);
        }

        @Override
        public void appendMessage(JsonNode message) {
            messages.add(message);
            storage.appendMessage(key, message);
            metadata.setMessageCount(messages.size());
        }

        @Override
        public JsonNode getModuleState(String moduleName) {
            return moduleStates.get(moduleName);
        }

        @Override
        public void setModuleState(String moduleName, JsonNode state) {
            moduleStates.put(moduleName, state);
        }

        @Override
        public Map<String, JsonNode> getAllModuleStates() {
            return Collections.unmodifiableMap(moduleStates);
        }

        @Override
        public void save() {
            storage.save(this);
        }

        @Override
        public void reload() {
            Session reloaded = storage.load(key);
            if (reloaded != null) {
                this.metadata = reloaded.getMetadata();
                this.messages.clear();
                this.messages.addAll(reloaded.getMessages());
            }
        }

        @Override
        public boolean delete() {
            return storage.delete(key);
        }
    }
}

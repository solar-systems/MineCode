package cn.abelib.minecode.session;

/**
 * 会话存储接口 - 支持多后端实现
 *
 * <p>不同的实现可以使用不同的存储后端：
 * <ul>
 *   <li>{@link JsonFileSessionStorage} - JSON 文件存储（默认）</li>
 *   <li>{@link JsonlSessionStorage} - JSONL 增量追加存储</li>
 *   <li>MemorySessionStorage - 内存存储（测试用）</li>
 *   <li>RedisSessionStorage - Redis 存储（可选）</li>
 * </ul>
 *
 * @author Abel
 */
public interface SessionStorage {

    /**
     * 创建新会话
     *
     * @param key      会话键
     * @param metadata 会话元数据
     * @return 创建的会话
     */
    Session create(SessionKey key, SessionMetadata metadata);

    /**
     * 加载会话
     *
     * @param key 会话键
     * @return 会话对象，不存在返回 null
     */
    Session load(SessionKey key);

    /**
     * 保存会话
     *
     * @param session 会话对象
     */
    void save(Session session);

    /**
     * 删除会话
     *
     * @param key 会话键
     * @return 是否成功
     */
    boolean delete(SessionKey key);

    /**
     * 列出用户的会话
     *
     * @param userId 用户 ID，null 表示所有用户
     * @param limit  最大数量
     * @return 会话信息列表
     */
    java.util.List<SessionInfo> list(String userId, int limit);

    /**
     * 检查会话是否存在
     *
     * @param key 会话键
     * @return 是否存在
     */
    boolean exists(SessionKey key);
}

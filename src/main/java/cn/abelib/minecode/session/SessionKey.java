package cn.abelib.minecode.session;

/**
 * 会话复合键 - 支持多用户场景
 *
 * @param userId    用户 ID
 * @param sessionId 会话 ID
 * @author Abel
 */
public record SessionKey(String userId, String sessionId) {

    /**
     * 默认用户 ID（单机场景）
     */
    public static final String DEFAULT_USER = "default";

    /**
     * 创建会话键
     */
    public static SessionKey of(String userId, String sessionId) {
        return new SessionKey(userId, sessionId);
    }

    /**
     * 创建默认用户的会话键
     */
    public static SessionKey of(String sessionId) {
        return new SessionKey(DEFAULT_USER, sessionId);
    }

    /**
     * 生成新的会话 ID
     */
    public static String generateSessionId() {
        return "session_" + System.currentTimeMillis();
    }

    /**
     * 转换为存储路径
     */
    public String toPath() {
        if (DEFAULT_USER.equals(userId)) {
            return sessionId;
        }
        return userId + "/" + sessionId;
    }

    @Override
    public String toString() {
        return DEFAULT_USER.equals(userId)
                ? sessionId
                : userId + ":" + sessionId;
    }
}

package cn.abelib.minecode.session;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 会话信息（用于列表展示）
 *
 * @param id           会话 ID
 * @param title        标题
 * @param model        模型名称
 * @param createdAt    创建时间
 * @param updatedAt    更新时间
 * @param messageCount 消息数量
 * @param totalTokens  总 Token 数
 * @param userId       用户 ID
 * @author Abel
 */
public record SessionInfo(
        String id,
        String title,
        String model,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        int messageCount,
        long totalTokens,
        String userId
) {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 格式化显示
     */
    public String format() {
        String time = (updatedAt != null ? updatedAt : createdAt).format(FORMATTER);
        return String.format("[%s] %s (%d messages, %s)", time, title, messageCount, model);
    }

    @Override
    public String toString() {
        return format();
    }

    /**
     * 从元数据创建
     */
    public static SessionInfo from(SessionMetadata metadata, String userId) {
        return new SessionInfo(
                metadata.getId(),
                metadata.getTitle(),
                metadata.getModel(),
                metadata.getCreatedAt(),
                metadata.getUpdatedAt(),
                metadata.getMessageCount(),
                metadata.getTotalTokens(),
                userId
        );
    }
}

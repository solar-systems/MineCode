package cn.abelib.minecode.session;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 会话元数据
 *
 * @author Abel
 */
public class SessionMetadata {

    private String id;
    private String title;
    private String model;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int messageCount;
    private long totalTokens;
    private Map<String, String> tags;
    private String parentId;  // 父会话（用于分支）

    public SessionMetadata() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.tags = new HashMap<>();
    }

    public SessionMetadata(String id, String title, String model) {
        this();
        this.id = id;
        this.title = title;
        this.model = model;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(long totalTokens) {
        this.totalTokens = totalTokens;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    /**
     * 更新修改时间
     */
    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 添加标签
     */
    public void addTag(String key, String value) {
        this.tags.put(key, value);
    }

    /**
     * 获取标签
     */
    public String getTag(String key) {
        return this.tags.get(key);
    }
}

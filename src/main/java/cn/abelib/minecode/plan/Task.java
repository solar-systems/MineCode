package cn.abelib.minecode.plan;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 任务模型
 *
 * @author Abel
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Task {

    private String id;
    private String description;
    private TaskStatus status;
    private String result;
    private String error;
    private List<String> dependencies;
    private int priority;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public Task() {
        this.status = TaskStatus.PENDING;
        this.dependencies = new ArrayList<>();
        this.priority = 0;
        this.createdAt = LocalDateTime.now();
    }

    public Task(String id, String description) {
        this();
        this.id = id;
        this.description = description;
    }

    // ========== 状态检查方法 ==========

    public boolean isPending() {
        return status == TaskStatus.PENDING;
    }

    public boolean isInProgress() {
        return status == TaskStatus.IN_PROGRESS;
    }

    public boolean isCompleted() {
        return status == TaskStatus.COMPLETED;
    }

    public boolean isFailed() {
        return status == TaskStatus.FAILED;
    }

    public boolean isTerminal() {
        return status.isTerminal();
    }

    // ========== Getters and Setters ==========

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies != null ? dependencies : new ArrayList<>();
    }

    public void addDependency(String taskId) {
        if (this.dependencies == null) {
            this.dependencies = new ArrayList<>();
        }
        if (!this.dependencies.contains(taskId)) {
            this.dependencies.add(taskId);
        }
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    @Override
    public String toString() {
        return String.format("Task[%s: %s - %s]", id, status.getLabel(), description);
    }

    /**
     * 格式化输出（用于进度显示）
     */
    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append(status.getIcon()).append(" [").append(id).append("] ").append(description);

        if (isInProgress()) {
            sb.append(" (进行中)");
        } else if (isCompleted() && result != null && !result.isEmpty()) {
            sb.append("\n   结果：").append(truncate(result, 100));
        } else if (isFailed() && error != null && !error.isEmpty()) {
            sb.append("\n   错误：").append(truncate(error, 100));
        }

        return sb.toString();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}

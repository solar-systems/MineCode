package cn.abelib.minecode.hook;

import cn.abelib.minecode.llm.ToolCall;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 重试上下文 - 跟踪重试状态
 *
 * <p>用于 RetryHook 和 Agent 之间共享重试状态信息：
 * <ul>
 *   <li>重试次数统计</li>
 *   <li>总重试时间</li>
 *   <li>重试历史记录</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * RetryContext ctx = new RetryContext();
 *
 * // 记录重试
 * ctx.recordRetry("bash", toolCall, "Command timeout");
 *
 * // 获取统计
 * System.out.println("Total retries: " + ctx.getTotalRetryCount());
 * System.out.println("Tool retries: " + ctx.getToolRetryCount("bash"));
 *
 * // 检查是否应该放弃
 * if (ctx.getToolRetryCount("bash") >= 5) {
 *     // 达到重试上限
 * }
 * }</pre>
 *
 * @author Abel
 */
public class RetryContext {

    /**
     * 重试记录
     */
    public static class RetryRecord {
        private final String toolName;
        private final ToolCall toolCall;
        private final String errorReason;
        private final long timestamp;
        private final int attemptNumber;

        public RetryRecord(String toolName, ToolCall toolCall, String errorReason, int attemptNumber) {
            this.toolName = toolName;
            this.toolCall = toolCall;
            this.errorReason = errorReason;
            this.timestamp = System.currentTimeMillis();
            this.attemptNumber = attemptNumber;
        }

        public String getToolName() { return toolName; }
        public ToolCall getToolCall() { return toolCall; }
        public String getErrorReason() { return errorReason; }
        public long getTimestamp() { return timestamp; }
        public int getAttemptNumber() { return attemptNumber; }
    }

    // 重试计数（按工具名称）
    private final Map<String, AtomicInteger> toolRetryCount = new ConcurrentHashMap<>();

    // 总重试次数
    private final AtomicInteger totalRetryCount = new AtomicInteger(0);

    // 总重试时间（毫秒）
    private final AtomicLong totalRetryTimeMs = new AtomicLong(0);

    // 当前执行会话开始时间
    private final AtomicLong sessionStartTime = new AtomicLong(0);

    /**
     * 开始新的执行会话
     */
    public void startSession() {
        sessionStartTime.set(System.currentTimeMillis());
    }

    /**
     * 获取会话执行时间（毫秒）
     */
    public long getSessionTimeMs() {
        long start = sessionStartTime.get();
        return start > 0 ? System.currentTimeMillis() - start : 0;
    }

    /**
     * 记录一次重试
     *
     * @param toolName    工具名称
     * @param toolCall    工具调用
     * @param errorReason 错误原因
     * @return 当前重试次数
     */
    public int recordRetry(String toolName, ToolCall toolCall, String errorReason) {
        totalRetryCount.incrementAndGet();

        int count = toolRetryCount
                .computeIfAbsent(toolName, k -> new AtomicInteger(0))
                .incrementAndGet();

        return count;
    }

    /**
     * 记录重试时间
     *
     * @param durationMs 重试耗时（毫秒）
     */
    public void recordRetryTime(long durationMs) {
        totalRetryTimeMs.addAndGet(durationMs);
    }

    /**
     * 获取指定工具的重试次数
     */
    public int getToolRetryCount(String toolName) {
        AtomicInteger count = toolRetryCount.get(toolName);
        return count != null ? count.get() : 0;
    }

    /**
     * 获取总重试次数
     */
    public int getTotalRetryCount() {
        return totalRetryCount.get();
    }

    /**
     * 获取总重试时间（毫秒）
     */
    public long getTotalRetryTimeMs() {
        return totalRetryTimeMs.get();
    }

    /**
     * 获取平均重试时间（毫秒）
     */
    public long getAverageRetryTimeMs() {
        int count = totalRetryCount.get();
        return count > 0 ? totalRetryTimeMs.get() / count : 0;
    }

    /**
     * 重置指定工具的重试计数
     */
    public void resetToolCount(String toolName) {
        toolRetryCount.remove(toolName);
    }

    /**
     * 重置所有重试状态
     */
    public void reset() {
        toolRetryCount.clear();
        totalRetryCount.set(0);
        totalRetryTimeMs.set(0);
        sessionStartTime.set(0);
    }

    /**
     * 获取重试统计摘要
     */
    public String getSummary() {
        return String.format(
                "Retry 统计: Total=%d, Time=%dms (Avg=%.1fms), Tools=%s",
                getTotalRetryCount(),
                getTotalRetryTimeMs(),
                (double) getTotalRetryTimeMs() / Math.max(1, getTotalRetryCount()),
                toolRetryCount.keySet()
        );
    }

    @Override
    public String toString() {
        return "RetryContext{" +
                "totalRetryCount=" + totalRetryCount.get() +
                ", totalRetryTimeMs=" + totalRetryTimeMs.get() +
                ", toolRetryCount=" + toolRetryCount +
                '}';
    }
}

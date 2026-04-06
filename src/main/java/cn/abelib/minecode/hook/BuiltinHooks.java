package cn.abelib.minecode.hook;

import cn.abelib.minecode.llm.ToolCall;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 内置 Hook 实现
 *
 * @author Abel
 */
public class BuiltinHooks {

    /**
     * 日志 Hook - 打印执行过程
     */
    public static class LoggingHook implements Hook {
        private static final Logger log = LoggerFactory.getLogger(LoggingHook.class);
        private final boolean verbose;

        public LoggingHook() {
            this(false);
        }

        public LoggingHook(boolean verbose) {
            this.verbose = verbose;
        }

        @Override
        public HookEvent onEvent(HookEvent event) {
            if (event instanceof PreCallEvent e) {
                log.info("📥 用户输入: {}", truncate(e.getUserInput(), 50));
            } else if (event instanceof PostCallEvent e) {
                log.info("📤 完成 ({} 轮)", e.getRoundsUsed());
            } else if (event instanceof PreReasoningEvent e) {
                if (verbose) {
                    log.debug("🧠 开始推理...");
                }
            } else if (event instanceof PostReasoningEvent e) {
                if (e.hasToolCalls()) {
                    log.info("🔧 工具调用: {} 个", e.getToolCalls().size());
                }
            } else if (event instanceof PreActingEvent e) {
                ToolCall tc = e.getToolCall();
                log.info("⚡ 执行: {}", tc.name());
            } else if (event instanceof PostActingEvent e) {
                if (e.isSuccess()) {
                    log.debug("✅ 完成: {} ({}ms)", e.getToolCall().name(), e.getDurationMs());
                } else {
                    log.warn("❌ 失败: {}", e.getToolCall().name());
                }
            } else if (event instanceof ErrorEvent e) {
                log.error("❌ 错误 [{}]: {}", e.getPhase(), e.getError().getMessage());
            }
            return event;
        }

        @Override
        public int priority() {
            return 1000; // 低优先级，最后执行
        }

        @Override
        public String name() {
            return "LoggingHook";
        }
    }

    /**
     * Token 统计 Hook
     */
    public static class TokenStatsHook implements Hook {
        private long totalPromptTokens = 0;
        private long totalCompletionTokens = 0;
        private int callCount = 0;

        @Override
        public HookEvent onEvent(HookEvent event) {
            if (event instanceof PostReasoningEvent e) {
                totalPromptTokens += e.getPromptTokens();
                totalCompletionTokens += e.getCompletionTokens();
                callCount++;
            }
            return event;
        }

        @Override
        public int priority() {
            return 500;
        }

        @Override
        public String name() {
            return "TokenStatsHook";
        }

        public long getTotalPromptTokens() {
            return totalPromptTokens;
        }

        public long getTotalCompletionTokens() {
            return totalCompletionTokens;
        }

        public long getTotalTokens() {
            return totalPromptTokens + totalCompletionTokens;
        }

        public int getCallCount() {
            return callCount;
        }

        public void reset() {
            totalPromptTokens = 0;
            totalCompletionTokens = 0;
            callCount = 0;
        }

        public String getStats() {
            return String.format("Token 统计: Prompt=%d, Completion=%d, Total=%d, Calls=%d",
                    totalPromptTokens, totalCompletionTokens, getTotalTokens(), callCount);
        }
    }

    /**
     * 工具执行时间 Hook - 记录并统计工具执行时间
     *
     * <p>使用示例：
     * <pre>{@code
     * TimingHook timingHook = new TimingHook();
     * Agent agent = Agent.builder()
     *     .llm(llm)
     *     .hook(timingHook)
     *     .build();
     *
     * // 获取统计信息
     * System.out.println(timingHook.getStats());
     * }</pre>
     */
    public static class TimingHook implements Hook {
        private static final Logger log = LoggerFactory.getLogger(TimingHook.class);

        // 使用 ThreadLocal 保证线程安全
        private final ThreadLocal<Long> lastTimestamp = new ThreadLocal<>();

        // 使用 ConcurrentHashMap 保证线程安全的统计
        private final Map<String, List<Long>> toolDurations = new ConcurrentHashMap<>();
        private final AtomicLong totalDuration = new AtomicLong(0);
        private final AtomicInteger toolCount = new AtomicInteger(0);

        @Override
        public HookEvent onEvent(HookEvent event) {
            if (event instanceof PreActingEvent) {
                lastTimestamp.set(System.currentTimeMillis());
            } else if (event instanceof PostActingEvent e) {
                Long startTime = lastTimestamp.get();
                if (startTime != null) {
                    long duration = System.currentTimeMillis() - startTime;
                    String toolName = e.getToolCall().name();

                    // 记录每个工具的执行时间
                    toolDurations.computeIfAbsent(toolName, k -> new CopyOnWriteArrayList<>())
                            .add(duration);

                    // 更新总时间
                    totalDuration.addAndGet(duration);
                    toolCount.incrementAndGet();

                    // 清理 ThreadLocal
                    lastTimestamp.remove();

                    log.debug("Tool {} executed in {}ms", toolName, duration);
                }
            }
            return event;
        }

        /**
         * 获取指定工具的平均执行时间
         */
        public long getAverageDuration(String toolName) {
            List<Long> durations = toolDurations.get(toolName);
            if (durations == null || durations.isEmpty()) {
                return 0;
            }
            return (long) durations.stream().mapToLong(Long::longValue).average().orElse(0);
        }

        /**
         * 获取总执行时间
         */
        public long getTotalDuration() {
            return totalDuration.get();
        }

        /**
         * 获取工具执行次数
         */
        public int getToolCount() {
            return toolCount.get();
        }

        /**
         * 获取平均执行时间
         */
        public long getAverageDuration() {
            int count = toolCount.get();
            return count > 0 ? totalDuration.get() / count : 0;
        }

        /**
         * 获取统计信息
         */
        public String getStats() {
            return String.format("Timing 统计: Total=%dms, Count=%d, Average=%dms",
                    getTotalDuration(), getToolCount(), getAverageDuration());
        }

        /**
         * 重置统计
         */
        public void reset() {
            toolDurations.clear();
            totalDuration.set(0);
            toolCount.set(0);
        }

        @Override
        public int priority() {
            return 501;
        }

        @Override
        public String name() {
            return "TimingHook";
        }
    }

    /**
     * 人工介入 Hook (HITL)
     *
     * <p>在工具执行前暂停，等待人工确认
     */
    public static class HumanInTheLoopHook implements Hook {
        private final boolean enabled;
        private final ConfirmationHandler handler;

        public interface ConfirmationHandler {
            boolean confirm(String toolName, String arguments);
        }

        public HumanInTheLoopHook(ConfirmationHandler handler) {
            this.enabled = true;
            this.handler = handler;
        }

        public HumanInTheLoopHook(boolean enabled, ConfirmationHandler handler) {
            this.enabled = enabled;
            this.handler = handler;
        }

        @Override
        public HookEvent onEvent(HookEvent event) {
            if (!enabled) {
                return event;
            }

            if (event instanceof PreActingEvent e) {
                ToolCall tc = e.getToolCall();
                boolean confirmed = handler.confirm(tc.name(), tc.arguments().toString());

                if (!confirmed) {
                    event.stopAgent();
                    return event;
                }
            }
            return event;
        }

        @Override
        public int priority() {
            return 10; // 高优先级
        }

        @Override
        public String name() {
            return "HumanInTheLoopHook";
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    /**
     * 无限循环检测 Hook
     *
     * <p>检测 Agent 是否陷入无限循环：
     * <ul>
     *   <li>重复的工具调用（相同工具 + 相同参数）</li>
     *   <li>相似的推理内容</li>
     *   <li>连续相同模式</li>
     * </ul>
     *
     * <p>使用示例：
     * <pre>{@code
     * // 默认配置：相同调用 3 次触发
     * Hook loopDetection = new BuiltinHooks.LoopDetectionHook();
     *
     * // 自定义配置
     * Hook loopDetection = new BuiltinHooks.LoopDetectionHook(
     *     LoopDetectionConfig.builder()
     *         .maxRepeatedCalls(5)
     *         .maxSimilarContent(3)
     *         .build()
     * );
     *
     * Agent agent = Agent.builder()
     *     .llm(llm)
     *     .hook(loopDetection)
     *     .build();
     * }</pre>
     */
    public static class LoopDetectionHook implements Hook {
        private static final Logger log = LoggerFactory.getLogger(LoopDetectionHook.class);

        private final LoopDetectionConfig config;
        // 使用线程安全的集合
        private final Map<String, Integer> toolCallCounts = new ConcurrentHashMap<>();
        private final List<String> recentContents = new CopyOnWriteArrayList<>();
        private final List<String> recentToolPatterns = new CopyOnWriteArrayList<>();
        private volatile int totalRounds = 0;
        private volatile String lastWarning = null;

        public LoopDetectionHook() {
            this(LoopDetectionConfig.defaultConfig());
        }

        public LoopDetectionHook(LoopDetectionConfig config) {
            this.config = config;
        }

        @Override
        public HookEvent onEvent(HookEvent event) {
            if (event instanceof PreCallEvent) {
                // 新对话开始，重置状态
                reset();
            } else if (event instanceof PostReasoningEvent e) {
                totalRounds++;

                // 检测重复推理内容
                if (e.getContent() != null && !e.getContent().isBlank()) {
                    checkRepeatedContent(e.getContent(), event);
                }

                // 检测工具调用模式
                if (e.hasToolCalls()) {
                    checkToolCallPattern(e.getToolCalls(), event);
                }

                // 检测总轮数
                if (totalRounds > config.getMaxTotalRounds()) {
                    String msg = String.format(
                            "检测到过多轮次 (%d > %d)，可能陷入循环",
                            totalRounds, config.getMaxTotalRounds());
                    interrupt(event, msg);
                }
            } else if (event instanceof PreActingEvent e) {
                // 检测重复工具调用
                checkRepeatedToolCall(e.getToolCall(), event);
            }

            return event;
        }

        /**
         * 检测重复的工具调用
         */
        private void checkRepeatedToolCall(ToolCall toolCall, HookEvent event) {
            String signature = buildToolSignature(toolCall);
            int count = toolCallCounts.merge(signature, 1, Integer::sum);

            if (count >= config.getMaxRepeatedCalls()) {
                String msg = String.format(
                        "检测到重复工具调用: %s (已调用 %d 次)\n" +
                                "工具: %s\n参数: %s",
                        signature, count, toolCall.name(),
                        truncate(toolCall.arguments().toString(), 200));
                interrupt(event, msg);
            }

            log.debug("工具调用计数: {} -> {}", signature, count);
        }

        /**
         * 检测重复的推理内容
         */
        private void checkRepeatedContent(String content, HookEvent event) {
            String normalized = normalizeContent(content);
            recentContents.add(normalized);

            // 保留最近 N 条（线程安全方式）
            while (recentContents.size() > config.getContentHistorySize()) {
                if (recentContents.size() > config.getContentHistorySize()) {
                    recentContents.remove(0);
                }
            }

            // 计算最近内容的重复率
            if (recentContents.size() >= 2) {
                int similarCount = 0;
                String last = recentContents.get(recentContents.size() - 1);

                for (int i = recentContents.size() - 2; i >= 0; i--) {
                    if (calculateSimilarity(last, recentContents.get(i)) > config.getSimilarityThreshold()) {
                        similarCount++;
                    }
                }

                if (similarCount >= config.getMaxSimilarContent()) {
                    String msg = String.format(
                            "检测到重复推理内容 (相似内容出现 %d 次)\n" +
                                    "最近内容: %s",
                            similarCount, truncate(content, 200));
                    interrupt(event, msg);
                }
            }
        }

        /**
         * 检测工具调用模式
         */
        private void checkToolCallPattern(List<ToolCall> toolCalls, HookEvent event) {
            String pattern = toolCalls.stream()
                    .map(ToolCall::name)
                    .sorted()
                    .collect(Collectors.joining("->"));

            recentToolPatterns.add(pattern);

            // 保留最近 N 条（线程安全方式）
            while (recentToolPatterns.size() > config.getPatternHistorySize()) {
                if (recentToolPatterns.size() > config.getPatternHistorySize()) {
                    recentToolPatterns.remove(0);
                }
            }

            // 检测重复模式
            if (recentToolPatterns.size() >= 3) {
                int patternCount = Collections.frequency(recentToolPatterns, pattern);
                if (patternCount >= config.getMaxRepeatedPattern()) {
                    String msg = String.format(
                            "检测到重复工具调用模式: %s (出现 %d 次)",
                            pattern, patternCount);
                    interrupt(event, msg);
                }
            }
        }

        /**
         * 构建工具调用签名
         */
        private String buildToolSignature(ToolCall toolCall) {
            String argsHash = hashArguments(toolCall.arguments());
            return toolCall.name() + ":" + argsHash;
        }

        /**
         * 对参数进行哈希（简化版，只取关键字段）
         */
        private String hashArguments(JsonNode arguments) {
            if (arguments == null || arguments.isEmpty()) {
                return "";
            }

            // 提取参数的主要特征
            List<String> keys = new ArrayList<>();
            arguments.fieldNames().forEachRemaining(keys::add);
            Collections.sort(keys);

            StringBuilder sb = new StringBuilder();
            for (String key : keys) {
                JsonNode value = arguments.get(key);
                String valueStr = value.isTextual() ? value.asText() : value.toString();
                if (valueStr.length() > 50) {
                    valueStr = valueStr.substring(0, 50);
                }
                sb.append(key).append("=").append(valueStr).append(";");
            }

            // 简单哈希
            return String.valueOf(sb.toString().hashCode());
        }

        /**
         * 归一化内容（去除空格、标点等）
         */
        private String normalizeContent(String content) {
            return content.replaceAll("\\s+", " ")
                    .replaceAll("[，。！？、,.!?]", "")
                    .trim()
                    .toLowerCase();
        }

        /**
         * 计算两个字符串的相似度（Jaccard 相似度）
         */
        private double calculateSimilarity(String s1, String s2) {
            if (s1.equals(s2)) return 1.0;
            if (s1.isEmpty() || s2.isEmpty()) return 0.0;

            Set<String> words1 = Arrays.stream(s1.split("\\s+"))
                    .collect(Collectors.toSet());
            Set<String> words2 = Arrays.stream(s2.split("\\s+"))
                    .collect(Collectors.toSet());

            Set<String> intersection = new HashSet<>(words1);
            intersection.retainAll(words2);

            Set<String> union = new HashSet<>(words1);
            union.addAll(words2);

            return (double) intersection.size() / union.size();
        }

        /**
         * 中断执行
         */
        private void interrupt(HookEvent event, String message) {
            if (config.isLogWarnings()) {
                log.warn("⚠️ {}", message);
            }
            lastWarning = message;
            event.stopAgent();
        }

        /**
         * 重置状态
         */
        public void reset() {
            toolCallCounts.clear();
            recentContents.clear();
            recentToolPatterns.clear();
            totalRounds = 0;
            lastWarning = null;
        }

        /**
         * 获取最后的警告信息
         */
        public String getLastWarning() {
            return lastWarning;
        }

        /**
         * 获取工具调用统计
         */
        public Map<String, Integer> getToolCallStats() {
            return new LinkedHashMap<>(toolCallCounts);
        }

        @Override
        public int priority() {
            return 50; // 高优先级，尽早检测
        }

        @Override
        public String name() {
            return "LoopDetectionHook";
        }
    }

    /**
     * 循环检测配置
     */
    public static class LoopDetectionConfig {
        private final int maxRepeatedCalls;       // 相同工具调用最大次数
        private final int maxSimilarContent;      // 相似内容最大出现次数
        private final int maxRepeatedPattern;     // 重复模式最大出现次数
        private final int maxTotalRounds;         // 最大总轮数
        private final double similarityThreshold; // 相似度阈值
        private final int contentHistorySize;     // 内容历史大小
        private final int patternHistorySize;     // 模式历史大小
        private final boolean logWarnings;        // 是否记录警告日志

        private LoopDetectionConfig(Builder builder) {
            this.maxRepeatedCalls = builder.maxRepeatedCalls;
            this.maxSimilarContent = builder.maxSimilarContent;
            this.maxRepeatedPattern = builder.maxRepeatedPattern;
            this.maxTotalRounds = builder.maxTotalRounds;
            this.similarityThreshold = builder.similarityThreshold;
            this.contentHistorySize = builder.contentHistorySize;
            this.patternHistorySize = builder.patternHistorySize;
            this.logWarnings = builder.logWarnings;
        }

        public static LoopDetectionConfig defaultConfig() {
            return builder().build();
        }

        public static Builder builder() {
            return new Builder();
        }

        public int getMaxRepeatedCalls() { return maxRepeatedCalls; }
        public int getMaxSimilarContent() { return maxSimilarContent; }
        public int getMaxRepeatedPattern() { return maxRepeatedPattern; }
        public int getMaxTotalRounds() { return maxTotalRounds; }
        public double getSimilarityThreshold() { return similarityThreshold; }
        public int getContentHistorySize() { return contentHistorySize; }
        public int getPatternHistorySize() { return patternHistorySize; }
        public boolean isLogWarnings() { return logWarnings; }

        public static class Builder {
            private int maxRepeatedCalls = 3;
            private int maxSimilarContent = 2;
            private int maxRepeatedPattern = 3;
            private int maxTotalRounds = 50;
            private double similarityThreshold = 0.7;
            private int contentHistorySize = 10;
            private int patternHistorySize = 10;
            private boolean logWarnings = true;

            /**
             * 相同工具调用的最大次数（超过则中断）
             */
            public Builder maxRepeatedCalls(int maxRepeatedCalls) {
                this.maxRepeatedCalls = maxRepeatedCalls;
                return this;
            }

            /**
             * 相似内容的最大出现次数
             */
            public Builder maxSimilarContent(int maxSimilarContent) {
                this.maxSimilarContent = maxSimilarContent;
                return this;
            }

            /**
             * 重复工具模式的最大出现次数
             */
            public Builder maxRepeatedPattern(int maxRepeatedPattern) {
                this.maxRepeatedPattern = maxRepeatedPattern;
                return this;
            }

            /**
             * 最大推理轮数
             */
            public Builder maxTotalRounds(int maxTotalRounds) {
                this.maxTotalRounds = maxTotalRounds;
                return this;
            }

            /**
             * 内容相似度阈值 (0-1)
             */
            public Builder similarityThreshold(double similarityThreshold) {
                this.similarityThreshold = similarityThreshold;
                return this;
            }

            /**
             * 是否记录警告日志
             */
            public Builder logWarnings(boolean logWarnings) {
                this.logWarnings = logWarnings;
                return this;
            }

            public LoopDetectionConfig build() {
                return new LoopDetectionConfig(this);
            }
        }
    }

    /**
     * 错误重试 Hook - 工具执行失败时自动重试
     *
     * <p>功能增强：
     * <ul>
     *   <li>支持自定义错误分类器 ({@link ErrorClassifier})</li>
     *   <li>支持 LLM 错误重试</li>
     *   <li>支持指数退避和最大延迟</li>
     *   <li>支持重试上下文跟踪</li>
     * </ul>
     *
     * <p>使用示例：
     * <pre>{@code
     * // 默认配置：失败后最多重试 3 次
     * Hook retryHook = new BuiltinHooks.RetryHook();
     *
     * // 自定义配置
     * Hook retryHook = new BuiltinHooks.RetryHook(
     *     RetryConfig.builder()
     *         .maxRetries(5)
     *         .retryDelayMs(1000)
     *         .exponentialBackoff(true)
     *         .errorClassifier((error, toolCall, result) -> {
     *             // 自定义错误分类逻辑
     *             if (error instanceof IOException) {
     *                 return ErrorClassifier.RetryDecision.RETRY;
     *             }
     *             return ErrorClassifier.RetryDecision.NO_RETRY;
     *         })
     *         .build()
     * );
     *
     * Agent agent = Agent.builder()
     *     .llm(llm)
     *     .hook(retryHook)
     *     .build();
     * }</pre>
     */
    public static class RetryHook implements Hook {
        private static final Logger log = LoggerFactory.getLogger(RetryHook.class);

        private final RetryConfig config;
        private final RetryContext retryContext;
        // 使用线程安全的集合
        private final Map<String, Integer> retryCount = new ConcurrentHashMap<>();
        private final Set<String> excludedTools;

        public RetryHook() {
            this(RetryConfig.defaultConfig());
        }

        public RetryHook(RetryConfig config) {
            this(config, new RetryContext());
        }

        public RetryHook(RetryConfig config, RetryContext retryContext) {
            this.config = config;
            this.retryContext = retryContext;
            this.excludedTools = new HashSet<>(config.getExcludedTools());
        }

        @Override
        public HookEvent onEvent(HookEvent event) {
            if (event instanceof PreCallEvent) {
                // 新对话开始，重置重试上下文
                retryContext.reset();
                retryContext.startSession();
            } else if (event instanceof PreActingEvent e) {
                // 重置重试计数
                String toolName = e.getToolCall().name();
                retryCount.remove(toolName);
            } else if (event instanceof PostActingEvent e) {
                // 检查是否需要重试
                if (!e.isSuccess()) {
                    ErrorClassifier.RetryDecision decision = classifyError(e);
                    return handleRetry(e, decision);
                }
            } else if (event instanceof ErrorEvent e) {
                // 处理 LLM 错误
                if (config.isRetryOnLLMError() && "reasoning".equals(e.getPhase())) {
                    return handleLLMError(e);
                }
            }
            return event;
        }

        /**
         * 分类错误并决定重试策略
         */
        private ErrorClassifier.RetryDecision classifyError(PostActingEvent event) {
            String toolName = event.getToolCall().name();

            // 排除的工具不重试
            if (excludedTools.contains(toolName)) {
                return ErrorClassifier.RetryDecision.NO_RETRY;
            }

            // 检查重试次数
            int count = retryCount.getOrDefault(toolName, 0);
            if (count >= config.getMaxRetries()) {
                return ErrorClassifier.RetryDecision.NO_RETRY;
            }

            // 使用错误分类器
            return config.getErrorClassifier().classify(
                    null,  // PostActingEvent 没有异常对象
                    event.getToolCall(),
                    event.getResult()
            );
        }

        /**
         * 处理重试
         */
        private HookEvent handleRetry(PostActingEvent event, ErrorClassifier.RetryDecision decision) {
            // SKIP 的情况
            if (decision == ErrorClassifier.RetryDecision.SKIP) {
                return event;
            }

            // NO_RETRY 的情况
            if (decision == ErrorClassifier.RetryDecision.NO_RETRY) {
                // 记录最终失败
                if (!config.getFinalErrorMessage().isEmpty()) {
                    event.setResult(config.getFinalErrorMessage() + "\n原始错误: " + event.getResult());
                }
                return event;
            }

            String toolName = event.getToolCall().name();
            int currentRetry = retryCount.getOrDefault(toolName, 0) + 1;
            retryCount.put(toolName, currentRetry);

            // 记录重试
            retryContext.recordRetry(toolName, event.getToolCall(), event.getResult());

            log.warn("工具 {} 执行失败，第 {}/{} 次重试 (decision={})",
                    toolName, currentRetry, config.getMaxRetries(), decision);

            try {
                // 计算延迟
                long delay = calculateDelay(currentRetry, decision);
                if (delay > 0) {
                    Thread.sleep(delay);
                }

                long startTime = System.currentTimeMillis();
                String newResult = executeTool(event.getToolCall());
                long duration = System.currentTimeMillis() - startTime;

                // 记录重试时间
                retryContext.recordRetryTime(duration);

                // 更新事件
                event.setResult(newResult);
                event.setDurationMs(duration);
                event.setSuccess(!isErrorResult(newResult));

                if (event.isSuccess()) {
                    log.info("工具 {} 重试成功", toolName);
                    retryCount.remove(toolName);
                } else if (currentRetry >= config.getMaxRetries()) {
                    log.error("工具 {} 重试 {} 次后仍然失败", toolName, currentRetry);
                    if (!config.getFinalErrorMessage().isEmpty()) {
                        event.setResult(config.getFinalErrorMessage() + "\n原始错误: " + newResult);
                    }
                }

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("重试被中断");
            } catch (Exception ex) {
                log.error("重试执行异常", ex);
                event.setResult("重试执行异常: " + ex.getMessage());
            }

            return event;
        }

        /**
         * 处理 LLM 错误
         */
        private HookEvent handleLLMError(ErrorEvent event) {
            // 记录 LLM 错误
            int llmRetries = retryContext.getToolRetryCount("__llm__");

            if (llmRetries >= config.getMaxLLMRetries()) {
                log.error("LLM 调用重试 {} 次后仍然失败", llmRetries);
                return event;
            }

            retryContext.recordRetry("__llm__", null, event.getError().getMessage());

            log.warn("LLM 调用失败，第 {}/{} 次重试: {}",
                    llmRetries + 1, config.getMaxLLMRetries(), event.getError().getMessage());

            // 注意：实际的重试逻辑需要在 Agent.chat() 中实现
            // 这里只是记录和通知

            return event;
        }

        /**
         * 计算重试延迟
         */
        private long calculateDelay(int retryCount, ErrorClassifier.RetryDecision decision) {
            long baseDelay = config.getRetryDelayMs();

            // 如果是 RETRY_WITH_BACKOFF 或者配置了 exponentialBackoff
            if (decision == ErrorClassifier.RetryDecision.RETRY_WITH_BACKOFF ||
                config.isExponentialBackoff()) {

                double multiplier = config.getBackoffMultiplier();
                long delay = (long) (baseDelay * Math.pow(multiplier, retryCount - 1));

                // 限制最大延迟
                return Math.min(delay, config.getMaxDelayMs());
            }

            return baseDelay;
        }

        /**
         * 执行工具
         */
        private String executeTool(ToolCall toolCall) {
            try {
                cn.abelib.minecode.tools.Tool tool =
                        cn.abelib.minecode.tools.ToolRegistry.getTool(toolCall.name());
                if (tool == null) {
                    return "Error: 未知工具 '" + toolCall.name() + "'";
                }
                return tool.execute(toolCall.arguments());
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        }

        /**
         * 判断结果是否为错误
         */
        private boolean isErrorResult(String result) {
            if (result == null) return true;
            String lower = result.toLowerCase();
            return lower.startsWith("error") || lower.startsWith("错误");
        }

        /**
         * 获取重试统计
         */
        public Map<String, Integer> getRetryStats() {
            return new LinkedHashMap<>(retryCount);
        }

        /**
         * 获取重试上下文
         */
        public RetryContext getRetryContext() {
            return retryContext;
        }

        /**
         * 重置重试计数
         */
        public void reset() {
            retryCount.clear();
            retryContext.reset();
        }

        @Override
        public int priority() {
            return 60; // 在日志 Hook 之后执行
        }

        @Override
        public String name() {
            return "RetryHook";
        }
    }

    /**
     * 重试配置
     */
    public static class RetryConfig {
        private final int maxRetries;
        private final long retryDelayMs;
        private final long maxDelayMs;
        private final boolean exponentialBackoff;
        private final double backoffMultiplier;
        private final List<String> excludedTools;
        private final String finalErrorMessage;
        private final ErrorClassifier errorClassifier;
        private final boolean retryOnLLMError;
        private final int maxLLMRetries;

        private RetryConfig(Builder builder) {
            this.maxRetries = builder.maxRetries;
            this.retryDelayMs = builder.retryDelayMs;
            this.maxDelayMs = builder.maxDelayMs;
            this.exponentialBackoff = builder.exponentialBackoff;
            this.backoffMultiplier = builder.backoffMultiplier;
            this.excludedTools = builder.excludedTools;
            this.finalErrorMessage = builder.finalErrorMessage;
            this.errorClassifier = builder.errorClassifier;
            this.retryOnLLMError = builder.retryOnLLMError;
            this.maxLLMRetries = builder.maxLLMRetries;
        }

        public static RetryConfig defaultConfig() {
            return builder().build();
        }

        public static Builder builder() {
            return new Builder();
        }

        public int getMaxRetries() { return maxRetries; }
        public long getRetryDelayMs() { return retryDelayMs; }
        public long getMaxDelayMs() { return maxDelayMs; }
        public boolean isExponentialBackoff() { return exponentialBackoff; }
        public double getBackoffMultiplier() { return backoffMultiplier; }
        public List<String> getExcludedTools() { return excludedTools; }
        public String getFinalErrorMessage() { return finalErrorMessage; }
        public ErrorClassifier getErrorClassifier() { return errorClassifier; }
        public boolean isRetryOnLLMError() { return retryOnLLMError; }
        public int getMaxLLMRetries() { return maxLLMRetries; }

        public static class Builder {
            private int maxRetries = 3;
            private long retryDelayMs = 500;
            private long maxDelayMs = 30000;  // 最大延迟 30 秒
            private boolean exponentialBackoff = true;
            private double backoffMultiplier = 2.0;
            private List<String> excludedTools = new ArrayList<>();
            private String finalErrorMessage = "";
            private ErrorClassifier errorClassifier = ErrorClassifier.defaultClassifier();
            private boolean retryOnLLMError = true;
            private int maxLLMRetries = 2;

            /**
             * 最大重试次数
             */
            public Builder maxRetries(int maxRetries) {
                this.maxRetries = maxRetries;
                return this;
            }

            /**
             * 重试延迟（毫秒）
             */
            public Builder retryDelayMs(long retryDelayMs) {
                this.retryDelayMs = retryDelayMs;
                return this;
            }

            /**
             * 最大延迟（毫秒）
             */
            public Builder maxDelayMs(long maxDelayMs) {
                this.maxDelayMs = maxDelayMs;
                return this;
            }

            /**
             * 是否使用指数退避
             */
            public Builder exponentialBackoff(boolean exponentialBackoff) {
                this.exponentialBackoff = exponentialBackoff;
                return this;
            }

            /**
             * 退避倍数（默认 2.0）
             */
            public Builder backoffMultiplier(double backoffMultiplier) {
                this.backoffMultiplier = backoffMultiplier;
                return this;
            }

            /**
             * 排除的工具（不重试）
             */
            public Builder excludeTools(String... tools) {
                this.excludedTools = Arrays.asList(tools);
                return this;
            }

            /**
             * 最终失败时的错误消息前缀
             */
            public Builder finalErrorMessage(String message) {
                this.finalErrorMessage = message;
                return this;
            }

            /**
             * 设置错误分类器
             */
            public Builder errorClassifier(ErrorClassifier classifier) {
                this.errorClassifier = classifier;
                return this;
            }

            /**
             * 是否在 LLM 错误时重试
             */
            public Builder retryOnLLMError(boolean retry) {
                this.retryOnLLMError = retry;
                return this;
            }

            /**
             * LLM 错误最大重试次数
             */
            public Builder maxLLMRetries(int maxRetries) {
                this.maxLLMRetries = maxRetries;
                return this;
            }

            public RetryConfig build() {
                return new RetryConfig(this);
            }
        }
    }
}

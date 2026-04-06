package cn.abelib.minecode.hook;

import cn.abelib.minecode.llm.ToolCall;

/**
 * 错误分类器 - 判断错误是否可重试
 *
 * <p>用于 RetryHook 判断特定错误是否应该重试。
 * 提供灵活的错误分类策略：
 * <ul>
 *   <li>基于错误类型（IOException, TimeoutException 等）</li>
 *   <li>基于错误消息模式</li>
 *   <li>基于工具名称</li>
 *   <li>基于执行结果内容</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * // 自定义错误分类器
 * ErrorClassifier classifier = (error, toolCall, result) -> {
 *     // 网络错误重试
 *     if (error instanceof IOException) {
 *         return RetryDecision.RETRY;
 *     }
 *     // 超时错误重试
 *     if (error instanceof TimeoutException) {
 *         return RetryDecision.RETRY_WITH_BACKOFF;
 *     }
 *     // 权限错误不重试
 *     if (result != null && result.contains("permission denied")) {
 *         return RetryDecision.NO_RETRY;
 *     }
 *     // 其他情况跳过
 *     return RetryDecision.SKIP;
 * };
 *
 * RetryHook retryHook = new RetryHook(
 *     RetryConfig.builder()
 *         .errorClassifier(classifier)
 *         .build()
 * );
 * }</pre>
 *
 * @author Abel
 */
@FunctionalInterface
public interface ErrorClassifier {

    /**
     * 分类错误并决定重试策略
     *
     * @param error    发生的异常（可为 null，如工具返回错误字符串）
     * @param toolCall 工具调用信息
     * @param result   工具执行结果（可能包含错误信息）
     * @return 重试决策
     */
    RetryDecision classify(Throwable error, ToolCall toolCall, String result);

    /**
     * 重试决策枚举
     */
    enum RetryDecision {
        /**
         * 应该重试
         */
        RETRY,

        /**
         * 应该重试（使用指数退避）
         */
        RETRY_WITH_BACKOFF,

        /**
         * 不重试，记录错误
         */
        NO_RETRY,

        /**
         * 跳过此错误（不重试，不记录为失败）
         */
        SKIP
    }

    /**
     * 默认错误分类器
     *
     * <p>分类规则：
     * <ul>
     *   <li>网络相关异常 -> RETRY</li>
     *   <li>超时异常 -> RETRY_WITH_BACKOFF</li>
     *   <li>权限/认证错误 -> NO_RETRY</li>
     *   <li>工具返回错误字符串 -> RETRY</li>
     * </ul>
     */
    static ErrorClassifier defaultClassifier() {
        return (error, toolCall, result) -> {
            // 有异常的情况
            if (error != null) {
                String errorName = error.getClass().getSimpleName();

                // 网络相关错误 - 重试
                if (errorName.contains("IOException") ||
                    errorName.contains("SocketException") ||
                    errorName.contains("ConnectionException")) {
                    return RetryDecision.RETRY;
                }

                // 超时错误 - 使用指数退避重试
                if (errorName.contains("Timeout") ||
                    errorName.contains("TimeoutException")) {
                    return RetryDecision.RETRY_WITH_BACKOFF;
                }

                // 权限/认证错误 - 不重试
                if (errorName.contains("Permission") ||
                    errorName.contains("Auth") ||
                    errorName.contains("Security")) {
                    return RetryDecision.NO_RETRY;
                }

                // 其他异常 - 默认重试
                return RetryDecision.RETRY;
            }

            // 没有异常，检查结果字符串
            if (result != null) {
                String lowerResult = result.toLowerCase();

                // 权限错误 - 不重试
                if (lowerResult.contains("permission denied") ||
                    lowerResult.contains("access denied") ||
                    lowerResult.contains("unauthorized")) {
                    return RetryDecision.NO_RETRY;
                }

                // 参数错误 - 不重试
                if (lowerResult.contains("invalid argument") ||
                    lowerResult.contains("invalid parameter") ||
                    lowerResult.contains("not found")) {
                    return RetryDecision.NO_RETRY;
                }

                // 结果包含错误 - 尝试重试
                if (lowerResult.startsWith("error") ||
                    lowerResult.contains("failed") ||
                    lowerResult.contains("timeout")) {
                    return RetryDecision.RETRY;
                }
            }

            // 默认不重试
            return RetryDecision.NO_RETRY;
        };
    }

    /**
     * 组合多个分类器（AND 逻辑）
     *
     * <p>只有所有分类器都同意重试时才重试。
     */
    default ErrorClassifier and(ErrorClassifier other) {
        return (error, toolCall, result) -> {
            RetryDecision d1 = this.classify(error, toolCall, result);
            RetryDecision d2 = other.classify(error, toolCall, result);

            // 如果任一分类器说 SKIP，则跳过
            if (d1 == RetryDecision.SKIP || d2 == RetryDecision.SKIP) {
                return RetryDecision.SKIP;
            }

            // 如果任一分类器说 NO_RETRY，则不重试
            if (d1 == RetryDecision.NO_RETRY || d2 == RetryDecision.NO_RETRY) {
                return RetryDecision.NO_RETRY;
            }

            // 如果任一分类器说 RETRY_WITH_BACKOFF，则使用退避
            if (d1 == RetryDecision.RETRY_WITH_BACKOFF || d2 == RetryDecision.RETRY_WITH_BACKOFF) {
                return RetryDecision.RETRY_WITH_BACKOFF;
            }

            return RetryDecision.RETRY;
        };
    }

    /**
     * 组合多个分类器（OR 逻辑）
     *
     * <p>只要有一个分类器同意重试就重试。
     */
    default ErrorClassifier or(ErrorClassifier other) {
        return (error, toolCall, result) -> {
            RetryDecision d1 = this.classify(error, toolCall, result);
            RetryDecision d2 = other.classify(error, toolCall, result);

            // 如果任一分类器说 SKIP，则跳过
            if (d1 == RetryDecision.SKIP || d2 == RetryDecision.SKIP) {
                return RetryDecision.SKIP;
            }

            // 如果任一分类器说 RETRY 或 RETRY_WITH_BACKOFF
            if (d1 == RetryDecision.RETRY || d2 == RetryDecision.RETRY) {
                return RetryDecision.RETRY;
            }

            if (d1 == RetryDecision.RETRY_WITH_BACKOFF || d2 == RetryDecision.RETRY_WITH_BACKOFF) {
                return RetryDecision.RETRY_WITH_BACKOFF;
            }

            return RetryDecision.NO_RETRY;
        };
    }
}

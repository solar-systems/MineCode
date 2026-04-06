package cn.abelib.minecode.hook;

import cn.abelib.minecode.agent.InterruptContext;
import cn.abelib.minecode.agent.InterruptReason;
import cn.abelib.minecode.llm.ToolCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 中断控制和错误重试测试
 *
 * @author Abel
 */
class InterruptAndRetryTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    // ==================== InterruptReason Tests ====================

    @Test
    void testInterruptReasonValues() {
        assertEquals(7, InterruptReason.values().length);

        // 验证每个枚举值
        assertNotNull(InterruptReason.USER_CANCEL);
        assertNotNull(InterruptReason.TIMEOUT);
        assertNotNull(InterruptReason.ERROR);
        assertNotNull(InterruptReason.EXTERNAL_SIGNAL);
        assertNotNull(InterruptReason.HOOK_INTERRUPT);
        assertNotNull(InterruptReason.RESOURCE_LIMIT);
        assertNotNull(InterruptReason.UNKNOWN);
    }

    @Test
    void testInterruptReasonRecoverable() {
        // 可恢复的中断
        assertTrue(InterruptReason.USER_CANCEL.isRecoverable());
        assertTrue(InterruptReason.TIMEOUT.isRecoverable());
        assertTrue(InterruptReason.EXTERNAL_SIGNAL.isRecoverable());
        assertTrue(InterruptReason.HOOK_INTERRUPT.isRecoverable());

        // 不可恢复的中断
        assertFalse(InterruptReason.ERROR.isRecoverable());
        assertFalse(InterruptReason.RESOURCE_LIMIT.isRecoverable());
        assertFalse(InterruptReason.UNKNOWN.isRecoverable());
    }

    @Test
    void testInterruptReasonDisplayName() {
        assertEquals("用户取消", InterruptReason.USER_CANCEL.getDisplayName());
        assertEquals("执行超时", InterruptReason.TIMEOUT.getDisplayName());
        assertEquals("错误", InterruptReason.ERROR.getDisplayName());
    }

    // ==================== InterruptContext Tests ====================

    @Test
    void testInterruptContextBasic() {
        InterruptContext ctx = new InterruptContext();

        // 初始状态
        assertFalse(ctx.isInterrupted());
        assertNull(ctx.getMessage());
        assertEquals(InterruptReason.UNKNOWN, ctx.getReason());
        assertNull(ctx.getCause());

        // 中断
        ctx.interrupt("Test interrupt", InterruptReason.USER_CANCEL);

        assertTrue(ctx.isInterrupted());
        assertEquals("Test interrupt", ctx.getMessage());
        assertEquals(InterruptReason.USER_CANCEL, ctx.getReason());
        assertTrue(ctx.isRecoverable());

        // 重置
        ctx.reset();
        assertFalse(ctx.isInterrupted());
        assertNull(ctx.getMessage());
    }

    @Test
    void testInterruptContextWithException() {
        InterruptContext ctx = new InterruptContext();
        Exception testException = new RuntimeException("Test error");

        ctx.interrupt("Error occurred", InterruptReason.ERROR, testException);

        assertTrue(ctx.isInterrupted());
        assertEquals("Error occurred", ctx.getMessage());
        assertEquals(InterruptReason.ERROR, ctx.getReason());
        assertFalse(ctx.isRecoverable());
        assertSame(testException, ctx.getCause());
    }

    @Test
    void testInterruptContextExecutionTime() {
        InterruptContext ctx = new InterruptContext();

        // 标记执行开始
        ctx.markExecutionStart();
        assertTrue(ctx.getExecutionTimeMs() >= 0);

        // 等待一小段时间
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            // ignore
        }

        // 验证执行时间增加
        assertTrue(ctx.getExecutionTimeMs() >= 10);
    }

    @Test
    void testInterruptContextListener() {
        InterruptContext ctx = new InterruptContext();
        StringBuilder sb = new StringBuilder();

        ctx.setListener((message, reason, cause) -> {
            sb.append(message).append("|").append(reason.name());
        });

        ctx.interrupt("Test", InterruptReason.TIMEOUT);

        assertTrue(sb.toString().contains("Test"));
        assertTrue(sb.toString().contains("TIMEOUT"));
    }

    @Test
    void testInterruptContextCheckInterrupted() {
        InterruptContext ctx = new InterruptContext();

        // 未中断时不抛出异常
        assertDoesNotThrow(ctx::checkInterrupted);

        // 中断后抛出异常
        ctx.interrupt("Test");
        assertThrows(InterruptedException.class, ctx::checkInterrupted);
    }

    @Test
    void testInterruptContextRuntimeException() {
        InterruptContext ctx = new InterruptContext();

        // 未中断时不抛出异常
        assertDoesNotThrow(ctx::checkInterruptedRuntime);

        // 中断后抛出运行时异常
        ctx.interrupt("Test", InterruptReason.HOOK_INTERRUPT);
        assertThrows(InterruptContext.AgentInterruptedException.class, ctx::checkInterruptedRuntime);
    }

    // ==================== ErrorClassifier Tests ====================

    @Test
    void testErrorClassifierDefault() {
        ErrorClassifier classifier = ErrorClassifier.defaultClassifier();

        // 网络错误应该重试
        assertEquals(ErrorClassifier.RetryDecision.RETRY,
                classifier.classify(new java.io.IOException("Connection failed"), null, null));

        // 超时错误应该使用退避重试
        assertEquals(ErrorClassifier.RetryDecision.RETRY_WITH_BACKOFF,
                classifier.classify(new java.util.concurrent.TimeoutException(), null, null));

        // 权限错误不应该重试
        assertEquals(ErrorClassifier.RetryDecision.NO_RETRY,
                classifier.classify(null, null, "Error: permission denied"));
    }

    @Test
    void testErrorClassifierCustom() {
        ErrorClassifier customClassifier = (error, toolCall, result) -> {
            if (error instanceof java.io.IOException) {
                return ErrorClassifier.RetryDecision.RETRY;
            }
            return ErrorClassifier.RetryDecision.NO_RETRY;
        };

        assertEquals(ErrorClassifier.RetryDecision.RETRY,
                customClassifier.classify(new java.io.IOException("test"), null, null));

        assertEquals(ErrorClassifier.RetryDecision.NO_RETRY,
                customClassifier.classify(new RuntimeException("test"), null, null));
    }

    @Test
    void testErrorClassifierAnd() {
        ErrorClassifier c1 = (error, toolCall, result) -> ErrorClassifier.RetryDecision.RETRY;
        ErrorClassifier c2 = (error, toolCall, result) -> ErrorClassifier.RetryDecision.NO_RETRY;

        ErrorClassifier combined = c1.and(c2);

        // AND 逻辑：一个说不重试，结果就是不重试
        assertEquals(ErrorClassifier.RetryDecision.NO_RETRY,
                combined.classify(null, null, null));
    }

    @Test
    void testErrorClassifierOr() {
        ErrorClassifier c1 = (error, toolCall, result) -> ErrorClassifier.RetryDecision.RETRY;
        ErrorClassifier c2 = (error, toolCall, result) -> ErrorClassifier.RetryDecision.NO_RETRY;

        ErrorClassifier combined = c1.or(c2);

        // OR 逻辑：一个说重试，结果就是重试
        assertEquals(ErrorClassifier.RetryDecision.RETRY,
                combined.classify(null, null, null));
    }

    // ==================== RetryContext Tests ====================

    @Test
    void testRetryContextBasic() {
        RetryContext ctx = new RetryContext();

        assertEquals(0, ctx.getTotalRetryCount());
        assertEquals(0, ctx.getToolRetryCount("bash"));
    }

    @Test
    void testRetryContextRecordRetry() {
        RetryContext ctx = new RetryContext();
        ToolCall toolCall = createToolCall("bash", "{\"command\": \"ls\"}");

        int count = ctx.recordRetry("bash", toolCall, "Timeout");

        assertEquals(1, count);
        assertEquals(1, ctx.getToolRetryCount("bash"));
        assertEquals(1, ctx.getTotalRetryCount());

        // 再次记录
        count = ctx.recordRetry("bash", toolCall, "Timeout again");

        assertEquals(2, count);
        assertEquals(2, ctx.getToolRetryCount("bash"));
        assertEquals(2, ctx.getTotalRetryCount());
    }

    @Test
    void testRetryContextReset() {
        RetryContext ctx = new RetryContext();

        ctx.recordRetry("bash", null, "Error");
        ctx.recordRetry("read_file", null, "Error");

        assertEquals(2, ctx.getTotalRetryCount());

        ctx.reset();

        assertEquals(0, ctx.getTotalRetryCount());
        assertEquals(0, ctx.getToolRetryCount("bash"));
    }

    @Test
    void testRetryContextSummary() {
        RetryContext ctx = new RetryContext();

        ctx.recordRetry("bash", null, "Error 1");
        ctx.recordRetry("bash", null, "Error 2");

        String summary = ctx.getSummary();

        assertTrue(summary.contains("Total=2"));
        assertTrue(summary.contains("bash"));
    }

    // ==================== RetryConfig Tests ====================

    @Test
    void testRetryConfigDefault() {
        BuiltinHooks.RetryConfig config = BuiltinHooks.RetryConfig.defaultConfig();

        assertEquals(3, config.getMaxRetries());
        assertEquals(500, config.getRetryDelayMs());
        assertTrue(config.isExponentialBackoff());
        assertTrue(config.isRetryOnLLMError());
        assertNotNull(config.getErrorClassifier());
    }

    @Test
    void testRetryConfigBuilder() {
        ErrorClassifier customClassifier = (error, toolCall, result) ->
                ErrorClassifier.RetryDecision.RETRY;

        BuiltinHooks.RetryConfig config = BuiltinHooks.RetryConfig.builder()
                .maxRetries(5)
                .retryDelayMs(1000)
                .maxDelayMs(60000)
                .exponentialBackoff(false)
                .backoffMultiplier(3.0)
                .retryOnLLMError(false)
                .maxLLMRetries(3)
                .errorClassifier(customClassifier)
                .build();

        assertEquals(5, config.getMaxRetries());
        assertEquals(1000, config.getRetryDelayMs());
        assertEquals(60000, config.getMaxDelayMs());
        assertFalse(config.isExponentialBackoff());
        assertEquals(3.0, config.getBackoffMultiplier());
        assertFalse(config.isRetryOnLLMError());
        assertEquals(3, config.getMaxLLMRetries());
        assertSame(customClassifier, config.getErrorClassifier());
    }

    // ==================== Helper Methods ====================

    private ToolCall createToolCall(String name, String arguments) {
        ObjectNode args;
        try {
            args = mapper.readTree(arguments).deepCopy();
        } catch (Exception e) {
            args = mapper.createObjectNode();
        }
        return new ToolCall("call_123", name, args);
    }
}

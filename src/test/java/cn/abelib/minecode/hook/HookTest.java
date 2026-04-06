package cn.abelib.minecode.hook;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hook 系统测试
 *
 * @author Abel
 */
class HookTest {

    private HookManager hookManager;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        hookManager = new HookManager();
    }

    // ==================== HookManager Tests ====================

    @Test
    void testHookManager_register() {
        Hook hook = new TestHook("TestHook", 100);
        hookManager.register(hook);

        assertEquals(1, hookManager.size());
        assertEquals(hook, hookManager.getHooks().get(0));
    }

    @Test
    void testHookManager_registerAll() {
        Hook hook1 = new TestHook("Hook1", 100);
        Hook hook2 = new TestHook("Hook2", 50);

        hookManager.registerAll(java.util.List.of(hook1, hook2));

        assertEquals(2, hookManager.size());
        // 按优先级排序，Hook2 应该在前面
        assertEquals("Hook2", hookManager.getHooks().get(0).name());
    }

    @Test
    void testHookManager_remove() {
        Hook hook = new TestHook("TestHook", 100);
        hookManager.register(hook);

        hookManager.remove(hook);

        assertEquals(0, hookManager.size());
    }

    @Test
    void testHookManager_clear() {
        hookManager.register(new TestHook("Hook1", 100));
        hookManager.register(new TestHook("Hook2", 50));

        hookManager.clear();

        assertEquals(0, hookManager.size());
    }

    @Test
    void testHookManager_prioritySorting() {
        Hook low = new TestHook("Low", 500);
        Hook high = new TestHook("High", 10);
        Hook medium = new TestHook("Medium", 100);

        hookManager.register(low);
        hookManager.register(high);
        hookManager.register(medium);

        // 验证按优先级排序
        java.util.List<Hook> hooks = hookManager.getHooks();
        assertEquals("High", hooks.get(0).name());
        assertEquals("Medium", hooks.get(1).name());
        assertEquals("Low", hooks.get(2).name());
    }

    @Test
    void testHookManager_trigger() {
        StringBuilder sb = new StringBuilder();
        Hook hook = new Hook() {
            @Override
            public HookEvent onEvent(HookEvent event) {
                sb.append("processed");
                return event;
            }

            @Override
            public String name() {
                return "TestHook";
            }
        };

        hookManager.register(hook);

        // 使用实际的 HookEvent 子类 - PreCallEvent
        PreCallEvent event = new PreCallEvent(null, "test input");
        HookEvent result = hookManager.trigger(event);

        assertEquals("processed", sb.toString());
        assertSame(event, result);
    }

    @Test
    void testHookManager_triggerWithModification() {
        Hook modifyHook = new Hook() {
            @Override
            public HookEvent onEvent(HookEvent event) {
                // 可以修改事件内容
                return event;
            }

            @Override
            public String name() {
                return "ModifyHook";
            }
        };

        hookManager.register(modifyHook);

        PreCallEvent event = new PreCallEvent(null, "original");

        HookEvent result = hookManager.trigger(event);

        assertNotNull(result);
    }

    @Test
    void testHookManager_interrupt() {
        Hook interruptHook = new Hook() {
            @Override
            public HookEvent onEvent(HookEvent event) {
                event.stopAgent();
                return event;
            }

            @Override
            public String name() {
                return "InterruptHook";
            }
        };

        hookManager.register(interruptHook);

        PreCallEvent event = new PreCallEvent(null, "test");
        HookEvent result = hookManager.trigger(event);

        assertNull(result); // 被中断返回 null
    }

    @Test
    void testHookManager_eventFiltering() {
        StringBuilder sb = new StringBuilder();

        Hook filteredHook = new Hook() {
            @Override
            public HookEvent onEvent(HookEvent event) {
                sb.append("executed");
                return event;
            }

            @Override
            public Set<Class<? extends HookEvent>> supportedEvents() {
                return Set.of(PreCallEvent.class);
            }

            @Override
            public String name() {
                return "FilteredHook";
            }
        };

        hookManager.register(filteredHook);

        // 匹配的事件
        PreCallEvent matchingEvent = new PreCallEvent(null, "test");
        hookManager.trigger(matchingEvent);
        assertEquals("executed", sb.toString());
    }

    // ==================== HookBuilder Tests ====================

    @Test
    void testHookBuilder_custom() {
        StringBuilder sb = new StringBuilder();

        Hook hook = HookBuilder.custom("CustomHook", event -> {
            sb.append("custom");
        });

        assertEquals("CustomHook", hook.name());

        PreCallEvent event = new PreCallEvent(null, "test");
        hook.onEvent(event);

        assertEquals("custom", sb.toString());
    }

    @Test
    void testHookBuilder_withPriority() {
        Hook delegate = new TestHook("Delegate", 100);
        Hook prioritized = HookBuilder.withPriority(10, delegate);

        assertEquals(10, prioritized.priority());
        assertEquals("Delegate", prioritized.name());
    }

    @Test
    void testHookBuilder_conditional() {
        StringBuilder sb = new StringBuilder();

        Hook conditional = HookBuilder.conditional(
                event -> event instanceof PreCallEvent,
                new Hook() {
                    @Override
                    public HookEvent onEvent(HookEvent event) {
                        sb.append("conditional");
                        return event;
                    }

                    @Override
                    public String name() {
                        return "Inner";
                    }
                }
        );

        // 条件满足
        PreCallEvent event = new PreCallEvent(null, "test");
        conditional.onEvent(event);
        assertEquals("conditional", sb.toString());
    }

    @Test
    void testHookBuilder_chain() {
        StringBuilder sb = new StringBuilder();

        Hook hook1 = HookBuilder.custom("H1", e -> sb.append("1"));
        Hook hook2 = HookBuilder.custom("H2", e -> sb.append("2"));
        Hook hook3 = HookBuilder.custom("H3", e -> sb.append("3"));

        Hook chain = HookBuilder.chain(hook1, hook2, hook3);

        PreCallEvent event = new PreCallEvent(null, "test");
        chain.onEvent(event);

        assertEquals("123", sb.toString());
    }

    @Test
    void testHookBuilder_once() {
        StringBuilder sb = new StringBuilder();

        Hook once = HookBuilder.once(HookBuilder.custom("Once", e -> sb.append("x")));

        PreCallEvent event = new PreCallEvent(null, "test");

        // 第一次执行
        once.onEvent(event);
        assertEquals("x", sb.toString());

        // 第二次不执行
        sb.setLength(0);
        once.onEvent(event);
        assertEquals("", sb.toString());
    }

    // ==================== Built-in Hooks Tests ====================

    @Test
    void testTokenStatsHook() {
        var tokenHook = new BuiltinHooks.TokenStatsHook();

        // 验证初始状态
        assertEquals(0, tokenHook.getTotalTokens());
        assertEquals(0, tokenHook.getCallCount());

        tokenHook.reset();
        assertEquals(0, tokenHook.getTotalPromptTokens());
        assertEquals(0, tokenHook.getTotalCompletionTokens());
    }

    @Test
    void testLoopDetectionHook_similarityCalculation() {
        var config = BuiltinHooks.LoopDetectionConfig.defaultConfig();

        // 验证默认配置
        assertEquals(3, config.getMaxRepeatedCalls());
        assertEquals(50, config.getMaxTotalRounds());
        assertEquals(0.7, config.getSimilarityThreshold(), 0.01);
    }

    @Test
    void testLoopDetectionConfig_builder() {
        var config = BuiltinHooks.LoopDetectionConfig.builder()
                .maxRepeatedCalls(5)
                .maxTotalRounds(100)
                .similarityThreshold(0.8)
                .logWarnings(false)
                .build();

        assertEquals(5, config.getMaxRepeatedCalls());
        assertEquals(100, config.getMaxTotalRounds());
        assertEquals(0.8, config.getSimilarityThreshold(), 0.01);
        assertFalse(config.isLogWarnings());
    }

    @Test
    void testRetryConfig_builder() {
        var config = BuiltinHooks.RetryConfig.builder()
                .maxRetries(5)
                .retryDelayMs(1000)
                .exponentialBackoff(false)
                .finalErrorMessage("Max retries exceeded")
                .build();

        assertEquals(5, config.getMaxRetries());
        assertEquals(1000, config.getRetryDelayMs());
        assertFalse(config.isExponentialBackoff());
        assertEquals("Max retries exceeded", config.getFinalErrorMessage());
    }

    // ==================== HookEvent Tests ====================

    @Test
    void testHookEvent_stopAgent() {
        // 使用实际的 HookEvent 子类测试 stopAgent 功能
        PreCallEvent event = new PreCallEvent(null, "test");

        assertFalse(event.isStopped());

        event.stopAgent();

        assertTrue(event.isStopped());
    }

    @Test
    void testPreCallEvent() {
        PreCallEvent event = new PreCallEvent(null, "Hello World");

        assertEquals("Hello World", event.getUserInput());
        assertEquals("PRE_CALL", event.getEventType());
    }

    @Test
    void testPostCallEvent() {
        PostCallEvent event = new PostCallEvent(null, "Response", 5);

        assertEquals("Response", event.getResponse());
        assertEquals(5, event.getRoundsUsed());
        assertEquals("POST_CALL", event.getEventType());
    }

    @Test
    void testErrorEvent() {
        Exception error = new RuntimeException("Test error");
        ErrorEvent event = new ErrorEvent(null, error, "reasoning");

        assertEquals(error, event.getError());
        assertEquals("reasoning", event.getPhase());
        assertEquals("Test error", event.getErrorMessage());
        assertEquals("ERROR", event.getEventType());
    }

    // ==================== Helper Classes ====================

    private static class TestHook implements Hook {
        private final String name;
        private final int priority;

        TestHook(String name, int priority) {
            this.name = name;
            this.priority = priority;
        }

        @Override
        public HookEvent onEvent(HookEvent event) {
            return event;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public String name() {
            return name;
        }
    }
}

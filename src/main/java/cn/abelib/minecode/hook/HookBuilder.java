package cn.abelib.minecode.hook;

import cn.abelib.minecode.hook.BuiltinHooks.*;
import cn.abelib.minecode.hook.BuiltinHooks.HumanInTheLoopHook.ConfirmationHandler;
import cn.abelib.minecode.permission.PermissionHook;
import cn.abelib.minecode.permission.PermissionManager;
import cn.abelib.minecode.permission.PermissionPreset;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Hook 构建器 - 简化 Hook 创建
 *
 * <p>提供静态工厂方法快速创建常用 Hook。
 *
 * <p>使用示例：
 * <pre>{@code
 * // 创建内置 Hook
 * Hook logging = HookBuilder.logging(true);
 * Hook tokenStats = HookBuilder.tokenStats();
 * Hook retry = HookBuilder.retry(RetryConfig.builder().maxRetries(5).build());
 *
 * // 创建自定义 Hook
 * Hook custom = HookBuilder.custom("MyHook", event -> {
 *     if (event instanceof PreReasoningEvent e) {
 *         System.out.println("Preparing for reasoning...");
 *     }
 * });
 *
 * // 创建事件过滤 Hook
 * Hook actingOnly = HookBuilder.filtered(
 *     Set.of(PreActingEvent.class, PostActingEvent.class),
 *     event -> {
 *         System.out.println("Tool: " + ((ActingEvent) event).getToolCall().name());
 *         return event;
 *     }
 * );
 *
 * // 使用 Builder 创建 Agent
 * Agent agent = Agent.builder()
 *     .llm(llm)
 *     .hooks(List.of(
 *         HookBuilder.logging(),
 *         HookBuilder.tokenStats(),
 *         HookBuilder.retry(),
 *         HookBuilder.loopDetection()
 *     ))
 *     .build();
 * }</pre>
 *
 * @author Abel
 */
public final class HookBuilder {

    private HookBuilder() {
        // 工具类，禁止实例化
    }

    // ==================== 内置 Hook 工厂方法 ====================

    /**
     * 创建日志 Hook
     */
    public static Hook logging() {
        return new LoggingHook();
    }

    /**
     * 创建日志 Hook（详细模式）
     *
     * @param verbose 是否输出详细日志
     */
    public static Hook logging(boolean verbose) {
        return new LoggingHook(verbose);
    }

    /**
     * 创建 Token 统计 Hook
     */
    public static Hook tokenStats() {
        return new TokenStatsHook();
    }

    /**
     * 创建计时 Hook
     */
    public static Hook timing() {
        return new TimingHook();
    }

    /**
     * 创建人工介入 Hook
     *
     * @param handler 确认处理器
     */
    public static Hook hitl(ConfirmationHandler handler) {
        return new HumanInTheLoopHook(handler);
    }

    /**
     * 创建人工介入 Hook（带启用开关）
     *
     * @param enabled 是否启用
     * @param handler 确认处理器
     */
    public static Hook hitl(boolean enabled, ConfirmationHandler handler) {
        return new HumanInTheLoopHook(enabled, handler);
    }

    /**
     * 创建错误重试 Hook（默认配置）
     */
    public static Hook retry() {
        return new RetryHook();
    }

    /**
     * 创建错误重试 Hook（自定义配置）
     *
     * @param config 重试配置
     */
    public static Hook retry(RetryConfig config) {
        return new RetryHook(config);
    }

    /**
     * 创建循环检测 Hook（默认配置）
     */
    public static Hook loopDetection() {
        return new LoopDetectionHook();
    }

    /**
     * 创建循环检测 Hook（自定义配置）
     *
     * @param config 循环检测配置
     */
    public static Hook loopDetection(LoopDetectionConfig config) {
        return new LoopDetectionHook(config);
    }

    // ==================== 自定义 Hook 工厂方法 ====================

    /**
     * 创建自定义 Hook
     *
     * <p>使用示例：
     * <pre>{@code
     * Hook myHook = HookBuilder.custom("MyHook", event -> {
     *     if (event instanceof PreReasoningEvent e) {
     *         System.out.println("Preparing for reasoning...");
     *     }
     * });
     * }</pre>
     *
     * @param name    Hook 名称
     * @param handler 事件处理器
     * @return Hook 实例
     */
    public static Hook custom(String name, Consumer<HookEvent> handler) {
        return new Hook() {
            @Override
            public HookEvent onEvent(HookEvent event) {
                handler.accept(event);
                return event;
            }

            @Override
            public String name() {
                return name;
            }
        };
    }

    /**
     * 创建自定义 Hook（带返回值）
     *
     * @param name    Hook 名称
     * @param handler 事件处理器（可修改事件）
     * @return Hook 实例
     */
    public static Hook customWithReturn(String name, Function<HookEvent, HookEvent> handler) {
        return new Hook() {
            @Override
            public HookEvent onEvent(HookEvent event) {
                return handler.apply(event);
            }

            @Override
            public String name() {
                return name;
            }
        };
    }

    /**
     * 创建事件过滤 Hook
     *
     * <p>只处理指定类型的事件，跳过其他事件。
     *
     * <p>使用示例：
     * <pre>{@code
     * Hook actingOnly = HookBuilder.filtered(
     *     Set.of(PreActingEvent.class, PostActingEvent.class),
     *     event -> {
     *         ActingEvent e = (ActingEvent) event;
     *         System.out.println("Tool: " + e.getToolCall().name());
     *         return event;
     *     }
     * );
     * }</pre>
     *
     * @param eventTypes 支持的事件类型集合
     * @param handler    事件处理器
     * @return Hook 实例
     */
    public static Hook filtered(Set<Class<? extends HookEvent>> eventTypes,
                                Function<HookEvent, HookEvent> handler) {
        return new Hook() {
            @Override
            public HookEvent onEvent(HookEvent event) {
                return handler.apply(event);
            }

            @Override
            public Set<Class<? extends HookEvent>> supportedEvents() {
                return eventTypes;
            }
        };
    }

    /**
     * 创建优先级 Hook
     *
     * @param priority 优先级
     * @param delegate 被包装的 Hook
     * @return 带优先级的 Hook
     */
    public static Hook withPriority(int priority, Hook delegate) {
        return new Hook() {
            @Override
            public HookEvent onEvent(HookEvent event) {
                return delegate.onEvent(event);
            }

            @Override
            public int priority() {
                return priority;
            }

            @Override
            public String name() {
                return delegate.name();
            }
        };
    }

    /**
     * 创建条件 Hook
     *
     * <p>只在满足条件时执行。
     *
     * @param condition 条件判断函数
     * @param delegate  被包装的 Hook
     * @return 条件 Hook
     */
    public static Hook conditional(java.util.function.Predicate<HookEvent> condition, Hook delegate) {
        return new Hook() {
            @Override
            public HookEvent onEvent(HookEvent event) {
                if (condition.test(event)) {
                    return delegate.onEvent(event);
                }
                return event;
            }

            @Override
            public String name() {
                return "Conditional[" + delegate.name() + "]";
            }
        };
    }

    /**
     * 创建链式 Hook（按顺序执行多个 Hook）
     *
     * @param hooks Hook 数组
     * @return 组合 Hook
     */
    public static Hook chain(Hook... hooks) {
        return new Hook() {
            @Override
            public HookEvent onEvent(HookEvent event) {
                HookEvent current = event;
                for (Hook hook : hooks) {
                    current = hook.onEvent(current);
                }
                return current;
            }

            @Override
            public String name() {
                return "ChainHook";
            }
        };
    }

    /**
     * 创建一次性 Hook（只执行一次后自动禁用）
     *
     * @param delegate 被包装的 Hook
     * @return 一次性 Hook
     */
    public static Hook once(Hook delegate) {
        return new Hook() {
            private boolean executed = false;

            @Override
            public HookEvent onEvent(HookEvent event) {
                if (!executed) {
                    executed = true;
                    return delegate.onEvent(event);
                }
                return event;
            }

            @Override
            public String name() {
                return "Once[" + delegate.name() + "]";
            }
        };
    }

    // ==================== 权限 Hook 工厂方法 ====================

    /**
     * 创建权限 Hook（默认配置）
     *
     * <p>使用 ASK_USER 作为默认决策，需要手动配置权限规则。
     */
    public static Hook permission() {
        return new PermissionHook(new PermissionManager());
    }

    /**
     * 创建权限 Hook
     *
     * @param permissionManager 权限管理器
     */
    public static Hook permission(PermissionManager permissionManager) {
        return new PermissionHook(permissionManager);
    }

    /**
     * 创建权限 Hook（使用预设）
     *
     * <p>使用示例：
     * <pre>{@code
     * Hook permission = HookBuilder.permission(PermissionPreset.STANDARD);
     * }</pre>
     *
     * @param preset 权限预设
     */
    public static Hook permission(PermissionPreset preset) {
        PermissionManager manager = PermissionManager.builder()
                .preset(preset)
                .build();
        return new PermissionHook(manager);
    }

    /**
     * 创建权限 Hook（自定义配置）
     *
     * <p>使用示例：
     * <pre>{@code
     * Hook permission = HookBuilder.permission(builder -> builder
     *     .allow("read_file")
     *     .allow("glob")
     *     .deny("bash(rm *)")
     *     .askUser("bash(sudo *)")
     * );
     * }</pre>
     *
     * @param configurator 配置器
     */
    public static Hook permission(java.util.function.Consumer<PermissionManager.Builder> configurator) {
        PermissionManager.Builder builder = PermissionManager.builder();
        configurator.accept(builder);
        return new PermissionHook(builder.build());
    }
}

package cn.abelib.minecode.hook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Hook 管理器
 *
 * <p>负责注册、排序和执行 Hook 链，支持：
 * <ul>
 *   <li>优先级排序执行</li>
 *   <li>事件类型过滤</li>
 *   <li>链控制（中断、跳过）</li>
 *   <li>生命周期管理</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * HookManager manager = new HookManager();
 *
 * // 注册 Hook
 * manager.register(new LoggingHook());
 * manager.register(new TokenStatsHook());
 *
 * // 初始化所有 Hook
 * manager.initAll();
 *
 * // 触发事件
 * HookEvent result = manager.trigger(new PreReasoningEvent(agent, messages));
 *
 * // 销毁所有 Hook
 * manager.destroyAll();
 * }</pre>
 *
 * @author Abel
 */
public class HookManager {
    private static final Logger log = LoggerFactory.getLogger(HookManager.class);

    private final List<Hook> hooks;

    public HookManager() {
        this.hooks = new CopyOnWriteArrayList<>();
    }

    /**
     * 注册 Hook
     *
     * @param hook Hook 实例
     */
    public void register(Hook hook) {
        hooks.add(hook);
        sortByPriority();
        log.debug("Hook registered: {} (priority={})", hook.name(), hook.priority());
    }

    /**
     * 注册多个 Hook
     *
     * @param hooks Hook 列表
     */
    public void registerAll(List<Hook> hooks) {
        this.hooks.addAll(hooks);
        sortByPriority();
    }

    /**
     * 移除 Hook
     *
     * @param hook Hook 实例
     */
    public void remove(Hook hook) {
        hooks.remove(hook);
        log.debug("Hook removed: {}", hook.name());
    }

    /**
     * 清空所有 Hook
     */
    public void clear() {
        hooks.clear();
        log.debug("All hooks cleared");
    }

    /**
     * 获取所有 Hook
     *
     * @return Hook 列表（副本）
     */
    public List<Hook> getHooks() {
        return new ArrayList<>(hooks);
    }

    /**
     * 触发事件（按优先级依次执行 Hook）
     *
     * <p>执行流程：
     * <ol>
     *   <li>检查事件类型过滤（supportedEvents）</li>
     *   <li>执行 Hook.onEvent()</li>
     *   <li>检查是否中断（shouldContinue）</li>
     *   <li>检查是否跳过后续（shouldSkipRemaining）</li>
     * </ol>
     *
     * @param event 事件对象
     * @return 处理后的事件，如果被中断则返回 null
     */
    public HookEvent trigger(HookEvent event) {
        HookEvent current = event;
        boolean skipRemaining = false;

        for (Hook hook : hooks) {
            // 检查事件过滤
            if (!isSupported(hook, current)) {
                continue;
            }

            // 检查是否跳过后续
            if (skipRemaining) {
                break;
            }

            try {
                current = hook.onEvent(current);

                // 检查中断
                if (!hook.shouldContinue(current)) {
                    log.info("Hook {} interrupted the execution", hook.name());
                    return null;
                }

                // 检查跳过后续
                if (hook.shouldSkipRemaining(current)) {
                    skipRemaining = true;
                    log.debug("Hook {} requested to skip remaining hooks", hook.name());
                }

            } catch (Exception e) {
                log.error("Hook {} error: {}", hook.name(), e.getMessage(), e);
                // 继续执行其他 Hook（可配置）
            }
        }

        return current;
    }

    /**
     * 触发事件（简化版，不关心返回值）
     *
     * <p>用于通知类事件，如 ErrorEvent。
     *
     * @param event 事件对象
     */
    public void triggerSimple(HookEvent event) {
        for (Hook hook : hooks) {
            // 检查事件过滤
            if (!isSupported(hook, event)) {
                continue;
            }

            try {
                hook.onEvent(event);
            } catch (Exception e) {
                log.error("Hook {} error: {}", hook.name(), e.getMessage(), e);
            }
        }
    }

    /**
     * 检查 Hook 是否支持该事件类型
     *
     * @param hook  Hook 实例
     * @param event 事件对象
     * @return 是否支持
     */
    private boolean isSupported(Hook hook, HookEvent event) {
        Set<Class<? extends HookEvent>> supported = hook.supportedEvents();
        // 支持所有事件
        if (supported.contains(HookEvent.class)) {
            return true;
        }
        // 支持特定事件类型
        return supported.stream().anyMatch(cls -> cls.isInstance(event));
    }

    /**
     * 初始化所有 Hook
     *
     * <p>调用每个 Hook 的 init() 方法。
     */
    public void initAll() {
        for (Hook hook : hooks) {
            try {
                hook.init();
                log.debug("Hook {} initialized", hook.name());
            } catch (Exception e) {
                log.warn("Hook {} init failed: {}", hook.name(), e.getMessage());
            }
        }
    }

    /**
     * 销毁所有 Hook
     *
     * <p>调用每个 Hook 的 destroy() 方法。
     */
    public void destroyAll() {
        for (Hook hook : hooks) {
            try {
                hook.destroy();
                log.debug("Hook {} destroyed", hook.name());
            } catch (Exception e) {
                log.warn("Hook {} destroy failed: {}", hook.name(), e.getMessage());
            }
        }
    }

    /**
     * 获取 Hook 数量
     *
     * @return Hook 数量
     */
    public int size() {
        return hooks.size();
    }

    /**
     * 按优先级排序
     */
    private void sortByPriority() {
        hooks.sort(Comparator.comparingInt(Hook::priority));
    }
}

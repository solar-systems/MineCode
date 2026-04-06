package cn.abelib.minecode.hook;

import java.util.Set;

/**
 * Hook 接口 - Agent 执行过程中的拦截器
 *
 * <p>Hook 可以在 Agent 执行的各个阶段进行拦截：
 * <ul>
 *   <li>监听事件（日志、监控）</li>
 *   <li>修改上下文（注入提示词）</li>
 *   <li>中断执行（HITL 人工介入）</li>
 * </ul>
 *
 * <p><b>使用 sealed class 模式匹配：</b>
 * <pre>{@code
 * Hook smartHook = new Hook() {
 *     @Override
 *     public HookEvent onEvent(HookEvent event) {
 *         return switch (event) {
 *             case PreReasoningEvent e -> {
 *                 e.getMessages().add(systemHint);
 *                 yield e;
 *             }
 *             case PostReasoningEvent e -> {
 *                 if (e.isGotoReasoningRequested()) {
 *                     log.info("Goto reasoning requested");
 *                 }
 *                 yield e;
 *             }
 *             case PreActingEvent e -> {
 *                 log.info("Tool: {}", e.getToolCall().name());
 *                 yield e;
 *             }
 *             case ActingEvent e -> {
 *                 // 匹配所有 Acting 事件
 *                 yield e;
 *             }
 *             default -> event;
 *         };
 *     }
 * };
 * }</pre>
 *
 * <p><b>优先级说明：</b>
 * <ul>
 *   <li>0-50: 系统级 Hook（认证、安全）</li>
 *   <li>51-100: 高优先级 Hook（验证、预处理）</li>
 *   <li>101-500: 业务 Hook（默认）</li>
 *   <li>501-1000: 低优先级 Hook（日志、监控）</li>
 * </ul>
 *
 * @author Abel
 */
public interface Hook {

    /**
     * 处理事件
     *
     * @param event 事件对象
     * @return 处理后的事件（可以修改后返回，或直接返回原事件）
     */
    HookEvent onEvent(HookEvent event);

    /**
     * 优先级（数值越小优先级越高）
     *
     * @return 优先级数值，默认 100
     */
    default int priority() {
        return 100;
    }

    /**
     * 是否继续执行
     *
     * <p>返回 false 将中断 Agent 执行，用于实现 HITL（人工介入）
     *
     * @param event 当前事件
     * @return true 继续执行，false 中断
     */
    default boolean shouldContinue(HookEvent event) {
        return !event.isStopped();
    }

    /**
     * 支持的 event 类型
     *
     * <p>默认支持所有事件，子类可覆盖进行过滤以提高性能。
     *
     * @return 支持的事件类型集合
     */
    default Set<Class<? extends HookEvent>> supportedEvents() {
        return Set.of(HookEvent.class);
    }

    /**
     * 是否跳过后续 Hook
     *
     * <p>返回 true 将跳过优先级更低的 Hook。用于实现"终结者"模式。
     *
     * @param event 当前事件
     * @return true 跳过后续 Hook，false 继续执行
     */
    default boolean shouldSkipRemaining(HookEvent event) {
        return false;
    }

    /**
     * Hook 名称（用于日志和调试）
     *
     * @return Hook 名称
     */
    default String name() {
        return this.getClass().getSimpleName();
    }

    /**
     * 生命周期 - 初始化
     *
     * <p>Hook 注册时调用，用于初始化资源。
     */
    default void init() {
    }

    /**
     * 生命周期 - 销毁
     *
     * <p>Hook 移除时调用，用于清理资源。
     */
    default void destroy() {
    }
}

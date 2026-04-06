package cn.abelib.minecode.permission;

import cn.abelib.minecode.hook.ActingEvent;
import cn.abelib.minecode.hook.Hook;
import cn.abelib.minecode.hook.HookEvent;
import cn.abelib.minecode.hook.PreActingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * 权限 Hook - 集成到 Hook 系统
 *
 * <p>在工具执行前检查权限，根据权限决策：
 * <ul>
 *   <li>ALLOW: 继续执行</li>
 *   <li>DENY: 抛出 PermissionDeniedException</li>
 *   <li>ASK_USER: 调用确认处理器</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * // 创建权限管理器
 * PermissionManager permissionManager = PermissionManager.builder()
 *     .preset(PermissionPreset.STANDARD)
 *     .deny("Bash(rm -rf:*)")
 *     .userConfirmHandler((tool, args) -> {
 *         // 自定义用户确认逻辑
 *         return askUserInUI("Allow " + tool + "?") ? PermissionDecision.ALLOW : PermissionDecision.DENY;
 *     })
 *     .build();
 *
 * // 创建权限 Hook
 * Hook permissionHook = new PermissionHook(permissionManager);
 *
 * // 注册到 Agent
 * Agent agent = Agent.builder()
 *     .llm(llm)
 *     .hooks(List.of(permissionHook))
 *     .build();
 * }</pre>
 *
 * @author Abel
 */
public class PermissionHook implements Hook {
    private static final Logger log = LoggerFactory.getLogger(PermissionHook.class);

    private final PermissionManager permissionManager;
    private final boolean throwOnDeny;
    private final PermissionDecisionHandler customHandler;

    /**
     * 创建权限 Hook
     *
     * @param permissionManager 权限管理器
     */
    public PermissionHook(PermissionManager permissionManager) {
        this(permissionManager, true, null);
    }

    /**
     * 创建权限 Hook
     *
     * @param permissionManager 权限管理器
     * @param throwOnDeny       拒绝时是否抛出异常
     * @param customHandler     自定义决策处理器
     */
    public PermissionHook(PermissionManager permissionManager,
                          boolean throwOnDeny,
                          PermissionDecisionHandler customHandler) {
        this.permissionManager = permissionManager;
        this.throwOnDeny = throwOnDeny;
        this.customHandler = customHandler;
    }

    @Override
    public HookEvent onEvent(HookEvent event) {
        if (event instanceof PreActingEvent e) {
            return handlePreActingEvent(e);
        }
        return event;
    }

    /**
     * 处理 PreActingEvent
     */
    private HookEvent handlePreActingEvent(PreActingEvent event) {
        String toolName = event.getToolCall().name();
        String toolArgs = formatToolArgs(event);

        log.debug("Permission check: {}({})", toolName, toolArgs);

        PermissionDecision decision = permissionManager.checkWithConfirm(toolName, toolArgs);

        switch (decision) {
            case ALLOW -> {
                log.debug("Permission allowed: {}({})", toolName, toolArgs);
                return event;
            }

            case DENY -> {
                log.warn("Permission denied: {}({})", toolName, toolArgs);
                if (customHandler != null) {
                    return customHandler.handleDeny(event);
                }
                if (throwOnDeny) {
                    throw new PermissionDeniedException(toolName, toolArgs);
                }
                // 不抛异常，但停止执行
                event.stopAgent();
                return event;
            }

            case ASK_USER -> {
                // 如果到这里还没有确认，使用默认行为
                log.info("Permission requires user confirmation: {}({})", toolName, toolArgs);
                if (customHandler != null) {
                    return customHandler.handleAskUser(event);
                }
                // 默认拒绝
                if (throwOnDeny) {
                    throw new PermissionDeniedException(toolName, toolArgs, "User confirmation required");
                }
                event.stopAgent();
                return event;
            }

            default -> {
                return event;
            }
        }
    }

    /**
     * 格式化工具参数
     *
     * <p>针对不同工具类型提取关键参数：
     * <ul>
     *   <li>bash: 提取 command 字段</li>
     *   <li>write_file/edit_file/read_file: 提取 file_path 字段</li>
     *   <li>其他: 返回整个 arguments 的字符串表示</li>
     * </ul>
     */
    private String formatToolArgs(ActingEvent event) {
        try {
            var toolCall = event.getToolCall();
            var args = toolCall.arguments();

            if (args == null || args.isMissingNode() || args.isNull()) {
                return "";
            }

            // 针对 bash 工具，提取 command 字段
            if ("bash".equals(toolCall.name()) && args.has("command")) {
                return args.get("command").asText();
            }

            // 针对文件操作工具，提取 file_path 字段
            String toolName = toolCall.name();
            if (("write_file".equals(toolName) || "edit_file".equals(toolName) || "read_file".equals(toolName))
                    && args.has("file_path")) {
                return args.get("file_path").asText();
            }

            // 其他情况，返回 arguments 的字符串表示
            return args.toString();
        } catch (Exception e) {
            log.debug("Failed to format tool args: {}", e.getMessage());
            return "";
        }
    }

    @Override
    public Set<Class<? extends HookEvent>> supportedEvents() {
        return Set.of(PreActingEvent.class);
    }

    @Override
    public int priority() {
        // 权限检查应该是最高优先级
        return 10;
    }

    @Override
    public String name() {
        return "PermissionHook";
    }

    /**
     * 自定义决策处理器接口
     */
    @FunctionalInterface
    public interface PermissionDecisionHandler {
        /**
         * 处理拒绝决策
         */
        HookEvent handleDeny(PreActingEvent event);

        /**
         * 处理需要用户确认的决策（默认实现）
         */
        default HookEvent handleAskUser(PreActingEvent event) {
            event.stopAgent();
            return event;
        }
    }
}

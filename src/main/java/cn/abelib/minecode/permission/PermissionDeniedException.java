package cn.abelib.minecode.permission;

/**
 * 权限拒绝异常
 *
 * <p>当工具执行被权限系统拒绝时抛出。
 *
 * @author Abel
 */
public class PermissionDeniedException extends RuntimeException {

    private final String toolName;
    private final String toolArgs;
    private final PermissionRule matchedRule;

    public PermissionDeniedException(String toolName, String toolArgs) {
        super(String.format("Permission denied for tool: %s(%s)", toolName, toolArgs));
        this.toolName = toolName;
        this.toolArgs = toolArgs;
        this.matchedRule = null;
    }

    public PermissionDeniedException(String toolName, String toolArgs, PermissionRule matchedRule) {
        super(String.format("Permission denied for tool: %s(%s). Rule: %s",
                toolName, toolArgs, matchedRule.getPattern()));
        this.toolName = toolName;
        this.toolArgs = toolArgs;
        this.matchedRule = matchedRule;
    }

    public PermissionDeniedException(String toolName, String toolArgs, String reason) {
        super(String.format("Permission denied for tool: %s(%s). Reason: %s",
                toolName, toolArgs, reason));
        this.toolName = toolName;
        this.toolArgs = toolArgs;
        this.matchedRule = null;
    }

    public String getToolName() {
        return toolName;
    }

    public String getToolArgs() {
        return toolArgs;
    }

    public PermissionRule getMatchedRule() {
        return matchedRule;
    }
}

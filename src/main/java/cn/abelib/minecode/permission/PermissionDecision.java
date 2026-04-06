package cn.abelib.minecode.permission;

/**
 * 权限决策枚举
 *
 * @author Abel
 */
public enum PermissionDecision {
    /**
     * 允许执行
     */
    ALLOW,

    /**
     * 拒绝执行
     */
    DENY,

    /**
     * 需要用户确认
     */
    ASK_USER
}

package cn.abelib.minecode.agent;

/**
 * 中断原因枚举
 *
 * <p>用于分类和统计不同类型的中断：
 * <ul>
 *   <li>USER_CANCEL - 用户主动取消</li>
 *   <li>TIMEOUT - 执行超时</li>
 *   <li>ERROR - 错误导致中断</li>
 *   <li>EXTERNAL_SIGNAL - 外部信号（如 SIGINT）</li>
 *   <li>HOOK_INTERRUPT - Hook 中断（如权限拒绝）</li>
 *   <li>RESOURCE_LIMIT - 资源限制（如 Token 超限）</li>
 * </ul>
 *
 * @author Abel
 */
public enum InterruptReason {

    /**
     * 用户主动取消
     */
    USER_CANCEL("用户取消", true),

    /**
     * 执行超时
     */
    TIMEOUT("执行超时", true),

    /**
     * 错误导致中断
     */
    ERROR("错误", false),

    /**
     * 外部信号中断
     */
    EXTERNAL_SIGNAL("外部信号", true),

    /**
     * Hook 中断（如权限拒绝、循环检测）
     */
    HOOK_INTERRUPT("Hook 中断", true),

    /**
     * 资源限制（如 Token 超限、内存不足）
     */
    RESOURCE_LIMIT("资源限制", false),

    /**
     * 未知原因
     */
    UNKNOWN("未知", false);

    private final String displayName;
    private final boolean recoverable;

    InterruptReason(String displayName, boolean recoverable) {
        this.displayName = displayName;
        this.recoverable = recoverable;
    }

    /**
     * 获取显示名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 是否可恢复
     *
     * <p>可恢复的中断可以通过重试或调整参数继续执行，
     * 不可恢复的中断通常需要人工介入或修正逻辑。
     */
    public boolean isRecoverable() {
        return recoverable;
    }
}

package cn.abelib.minecode.plan;

/**
 * 任务状态枚举
 *
 * @author Abel
 */
public enum TaskStatus {

    /**
     * 待执行
     */
    PENDING("⚪", "待执行"),

    /**
     * 进行中
     */
    IN_PROGRESS("🔵", "进行中"),

    /**
     * 已完成
     */
    COMPLETED("✅", "已完成"),

    /**
     * 已失败
     */
    FAILED("❌", "已失败");

    private final String icon;
    private final String label;

    TaskStatus(String icon, String label) {
        this.icon = icon;
        this.label = label;
    }

    public String getIcon() {
        return icon;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 是否为终态（已完成或失败）
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}

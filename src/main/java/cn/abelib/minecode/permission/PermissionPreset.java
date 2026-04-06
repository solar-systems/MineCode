package cn.abelib.minecode.permission;

/**
 * 权限预设
 *
 * <p>提供常用的权限配置组合：
 * <ul>
 *   <li>READ_ONLY: 只读权限（Read, Glob, Grep）</li>
 *   <li>STANDARD: 标准权限（读写 + 危险命令需确认）</li>
 *   <li>SAFE_EDIT: 安全编辑（编辑 + 敏感路径保护）</li>
 *   <li>FULL_ACCESS: 完全访问权限</li>
 * </ul>
 *
 * @author Abel
 */
public enum PermissionPreset {
    /**
     * 只读权限
     * 允许: Read, Glob, Grep
     * 拒绝: Write, Edit, Bash
     */
    READ_ONLY,

    /**
     * 标准权限
     * 允许: Read, Glob, Grep, Write, Edit
     * 需确认: Bash(rm:*), Bash(sudo:*)
     */
    STANDARD,

    /**
     * 安全编辑权限
     * 允许: Read, Glob, Grep, Edit
     * 拒绝: Write(/etc/*), Write(/usr/*), Bash(rm -rf:*)
     * 需确认: Bash(sudo:*)
     */
    SAFE_EDIT,

    /**
     * 完全访问权限
     * 允许: 所有操作
     */
    FULL_ACCESS
}

package cn.abelib.minecode.permission;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 权限规则
 *
 * <p>支持多种匹配模式：
 * <ul>
 *   <li>精确匹配: "bash(ls -la)"</li>
 *   <li>前缀通配: "bash(rm *)" - 匹配所有 rm 开头的命令</li>
 *   <li>工具匹配: "read_file" - 匹配所有 read_file 操作</li>
 *   <li>路径通配: "write_file(/etc/*)" - 匹配所有 /etc 下的文件写入</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * PermissionRule rule1 = PermissionRule.allow("read_file");
 * PermissionRule rule2 = PermissionRule.deny("bash(rm *)");
 * PermissionRule rule3 = PermissionRule.askUser("bash(sudo *)");
 * PermissionRule rule4 = PermissionRule.allow("write_file(/home/user/*)");
 * }</pre>
 *
 * @author Abel
 */
public class PermissionRule {
    private final String pattern;
    private final PermissionDecision decision;
    private final String description;
    private final Pattern compiledPattern;

    private PermissionRule(String pattern, PermissionDecision decision, String description) {
        this.pattern = pattern;
        this.decision = decision;
        this.description = description;
        this.compiledPattern = compilePattern(pattern);
    }

    /**
     * 创建允许规则
     */
    public static PermissionRule allow(String pattern) {
        return new PermissionRule(pattern, PermissionDecision.ALLOW, null);
    }

    /**
     * 创建允许规则（带描述）
     */
    public static PermissionRule allow(String pattern, String description) {
        return new PermissionRule(pattern, PermissionDecision.ALLOW, description);
    }

    /**
     * 创建拒绝规则
     */
    public static PermissionRule deny(String pattern) {
        return new PermissionRule(pattern, PermissionDecision.DENY, null);
    }

    /**
     * 创建拒绝规则（带描述）
     */
    public static PermissionRule deny(String pattern, String description) {
        return new PermissionRule(pattern, PermissionDecision.DENY, description);
    }

    /**
     * 创建需确认规则
     */
    public static PermissionRule askUser(String pattern) {
        return new PermissionRule(pattern, PermissionDecision.ASK_USER, null);
    }

    /**
     * 创建需确认规则（带描述）
     */
    public static PermissionRule askUser(String pattern, String description) {
        return new PermissionRule(pattern, PermissionDecision.ASK_USER, description);
    }

    /**
     * 检查是否匹配工具调用
     *
     * @param toolName 工具名称
     * @param toolArgs 工具参数（可选）
     * @return 是否匹配
     */
    public boolean matches(String toolName, String toolArgs) {
        // 纯工具匹配 (如 "Read", "Write")
        if (!pattern.contains("(") && !pattern.contains(":")) {
            return pattern.equals(toolName);
        }

        // 带参数的模式匹配 (如 "Bash(ls)", "Bash(git:*)", "Write(/home/*)")
        String input = toolArgs != null && !toolArgs.isEmpty()
                ? toolName + "(" + toolArgs + ")"
                : toolName + "()";

        return compiledPattern.matcher(input).matches();
    }

    /**
     * 将模式编译为正则表达式
     */
    private static Pattern compilePattern(String pattern) {
        StringBuilder regex = new StringBuilder();
        int i = 0;
        int len = pattern.length();

        while (i < len) {
            char c = pattern.charAt(i);

            if (c == '*') {
                // 通配符转换为正则
                regex.append(".*");
            } else if (isRegexSpecialChar(c)) {
                // 转义正则特殊字符
                regex.append("\\").append(c);
            } else {
                regex.append(c);
            }
            i++;
        }

        return Pattern.compile("^" + regex + "$");
    }

    /**
     * 判断是否为正则特殊字符
     */
    private static boolean isRegexSpecialChar(char c) {
        return ".^$+{}[]|()".indexOf(c) >= 0;
    }

    public String getPattern() {
        return pattern;
    }

    public PermissionDecision getDecision() {
        return decision;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PermissionRule that = (PermissionRule) o;
        return Objects.equals(pattern, that.pattern) && decision == that.decision;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pattern, decision);
    }

    @Override
    public String toString() {
        return "PermissionRule{" +
                "pattern='" + pattern + '\'' +
                ", decision=" + decision +
                (description != null ? ", description='" + description + '\'' : "") +
                '}';
    }
}

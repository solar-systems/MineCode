package cn.abelib.minecode.skill;

/**
 * 技能接口 - 用户可调用的快捷命令
 *
 * <p>技能与工具的区别：
 * <ul>
 *   <li>技能：用户主动调用，如 /commit</li>
 *   <li>工具：LLM 自动决定调用，如 write_file</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * public class CommitSkill implements Skill {
 *     public String name() { return "commit"; }
 *     public String description() { return "Git 提交"; }
 *     public String execute(SkillContext ctx, String args) {
 *         // 执行 git commit
 *     }
 * }
 * }</pre>
 *
 * @author Abel
 */
public interface Skill {

    /**
     * 技能名称（不含斜杠）
     *
     * @return 名称，如 "commit"
     */
    String name();

    /**
     * 技能描述
     *
     * @return 描述文本
     */
    String description();

    /**
     * 技能用法
     *
     * @return 用法说明，如 "/commit [message]"
     */
    default String usage() {
        return "/" + name();
    }

    /**
     * 是否需要参数
     *
     * @return true 表示必须有参数
     */
    default boolean requiresArgs() {
        return false;
    }

    /**
     * 执行技能
     *
     * @param context 执行上下文
     * @param args    参数（可能为空字符串）
     * @return 执行结果
     */
    String execute(SkillContext context, String args);

    /**
     * 验证参数
     *
     * @param args 参数
     * @return 错误信息，null 表示验证通过
     */
    default String validateArgs(String args) {
        if (requiresArgs() && (args == null || args.isBlank())) {
            return "用法: " + usage();
        }
        return null;
    }
}

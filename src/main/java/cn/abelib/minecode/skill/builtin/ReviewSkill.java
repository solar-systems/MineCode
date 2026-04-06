package cn.abelib.minecode.skill.builtin;

import cn.abelib.minecode.skill.Skill;
import cn.abelib.minecode.skill.SkillContext;

/**
 * 代码审查技能
 *
 * @author Abel
 */
public class ReviewSkill implements Skill {

    @Override
    public String name() {
        return "review";
    }

    @Override
    public String description() {
        return "审查代码质量";
    }

    @Override
    public String usage() {
        return "/review [file]";
    }

    @Override
    public String execute(SkillContext context, String args) {
        String prompt;
        if (args != null && !args.isBlank()) {
            prompt = String.format("""
                    请审查以下文件的代码质量：
                    - 代码规范
                    - 潜在 bug
                    - 性能问题
                    - 可读性

                    文件: %s

                    请先读取文件内容，然后给出审查意见。
                    """, args.trim());
        } else {
            prompt = """
                    请审查当前代码库的代码质量：
                    - 检查最近的代码更改
                    - 找出可能存在的问题
                    - 提供改进建议

                    请使用 glob 和 grep 工具找到相关文件，然后进行审查。
                    """;
        }

        try {
            return context.getAgent().chat(prompt);
        } catch (Exception e) {
            return "审查失败: " + e.getMessage();
        }
    }
}

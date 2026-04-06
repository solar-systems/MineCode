package cn.abelib.minecode.skill.builtin;

import cn.abelib.minecode.skill.Skill;
import cn.abelib.minecode.skill.SkillContext;

/**
 * 重构建议技能
 *
 * @author Abel
 */
public class RefactorSkill implements Skill {

    @Override
    public String name() {
        return "refactor";
    }

    @Override
    public String description() {
        return "重构建议";
    }

    @Override
    public String usage() {
        return "/refactor [file]";
    }

    @Override
    public String execute(SkillContext context, String args) {
        String prompt;
        if (args != null && !args.isBlank()) {
            prompt = String.format("""
                    请分析以下文件并提供重构建议：
                    1. 代码结构问题
                    2. 可读性改进
                    3. 性能优化
                    4. 设计模式应用
                    5. 具体的重构步骤

                    文件: %s

                    请先读取文件，然后给出详细的重构建议（不要直接修改代码）。
                    """, args.trim());
        } else {
            prompt = """
                    请分析当前代码库并提供重构建议：
                    1. 找出代码质量问题
                    2. 建议改进方向
                    3. 优先级排序

                    请使用工具分析代码库后给出建议。
                    """;
        }

        try {
            return context.getAgent().chat(prompt);
        } catch (Exception e) {
            return "分析失败: " + e.getMessage();
        }
    }
}

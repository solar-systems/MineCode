package cn.abelib.minecode.skill.builtin;

import cn.abelib.minecode.skill.Skill;
import cn.abelib.minecode.skill.SkillContext;

/**
 * 解释代码技能
 *
 * @author Abel
 */
public class ExplainSkill implements Skill {

    @Override
    public String name() {
        return "explain";
    }

    @Override
    public String description() {
        return "解释代码逻辑";
    }

    @Override
    public String usage() {
        return "/explain [code or file]";
    }

    @Override
    public String execute(SkillContext context, String args) {
        String prompt;
        if (args != null && !args.isBlank()) {
            // 检查是否是文件路径
            if (args.contains(".") && !args.contains("\n")) {
                prompt = String.format("""
                        请解释以下文件中的代码：
                        1. 主要功能是什么
                        2. 核心逻辑流程
                        3. 使用的设计模式（如果有）
                        4. 关键函数的作用

                        文件: %s

                        请先读取文件内容，然后给出解释。
                        """, args.trim());
            } else {
                prompt = String.format("""
                        请解释以下代码：
                        1. 这段代码做什么
                        2. 逐行解释
                        3. 可能的改进建议

                        ```
                        %s
                        ```
                        """, args);
            }
        } else {
            prompt = "请解释最近修改的代码文件的功能和逻辑。";
        }

        try {
            return context.getAgent().chat(prompt);
        } catch (Exception e) {
            return "解释失败: " + e.getMessage();
        }
    }
}

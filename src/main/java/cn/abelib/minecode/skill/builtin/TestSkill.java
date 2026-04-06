package cn.abelib.minecode.skill.builtin;

import cn.abelib.minecode.skill.Skill;
import cn.abelib.minecode.skill.SkillContext;

/**
 * 生成测试技能
 *
 * @author Abel
 */
public class TestSkill implements Skill {

    @Override
    public String name() {
        return "test";
    }

    @Override
    public String description() {
        return "生成单元测试";
    }

    @Override
    public String usage() {
        return "/test [file]";
    }

    @Override
    public boolean requiresArgs() {
        return true;
    }

    @Override
    public String execute(SkillContext context, String args) {
        String prompt = String.format("""
                请为以下文件生成单元测试：
                1. 分析代码的主要功能
                2. 识别需要测试的方法
                3. 设计测试用例（正常、边界、异常）
                4. 生成 JUnit 测试代码

                文件: %s

                请先读取文件内容，然后生成完整的测试类代码。
                """, args.trim());

        try {
            return context.getAgent().chat(prompt);
        } catch (Exception e) {
            return "生成测试失败: " + e.getMessage();
        }
    }
}

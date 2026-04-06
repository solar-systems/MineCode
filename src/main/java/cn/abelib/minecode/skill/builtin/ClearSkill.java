package cn.abelib.minecode.skill.builtin;

import cn.abelib.minecode.skill.Skill;
import cn.abelib.minecode.skill.SkillContext;

/**
 * 清空对话技能
 *
 * @author Abel
 */
public class ClearSkill implements Skill {

    @Override
    public String name() {
        return "clear";
    }

    @Override
    public String description() {
        return "清空当前对话";
    }

    @Override
    public String execute(SkillContext context, String args) {
        context.getAgent().reset();
        return "✓ 对话已清空";
    }
}

package cn.abelib.minecode.skill.builtin;

import cn.abelib.minecode.skill.Skill;
import cn.abelib.minecode.skill.SkillContext;

/**
 * 显示配置技能
 *
 * @author Abel
 */
public class ConfigSkill implements Skill {

    @Override
    public String name() {
        return "config";
    }

    @Override
    public String description() {
        return "显示当前配置";
    }

    @Override
    public String execute(SkillContext context, String args) {
        context.getConfig().printConfig();
        return "";  // printConfig 已经输出了
    }
}

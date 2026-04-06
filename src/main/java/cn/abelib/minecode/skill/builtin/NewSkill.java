package cn.abelib.minecode.skill.builtin;

import cn.abelib.minecode.skill.Skill;
import cn.abelib.minecode.skill.SkillContext;

/**
 * 新建会话技能
 *
 * @author Abel
 */
public class NewSkill implements Skill {

    @Override
    public String name() {
        return "new";
    }

    @Override
    public String description() {
        return "创建新会话";
    }

    @Override
    public String execute(SkillContext context, String args) {
        // 保存当前会话
        if (!context.getAgent().getMessages().isEmpty()) {
            context.getSessionManager().saveSession(context.getAgent().getMessages());
        }

        // 重置并创建新会话
        context.getAgent().reset();
        String model = context.getConfig().get("model");
        context.getSessionManager().createNewSession(model);

        return "✓ 新会话已创建";
    }
}

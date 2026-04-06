package cn.abelib.minecode.skill.builtin;

import cn.abelib.minecode.skill.Skill;
import cn.abelib.minecode.skill.SkillContext;

import java.nio.file.Path;

/**
 * 保存会话技能
 *
 * @author Abel
 */
public class SaveSkill implements Skill {

    @Override
    public String name() {
        return "save";
    }

    @Override
    public String description() {
        return "保存当前会话";
    }

    @Override
    public String execute(SkillContext context, String args) {
        if (context.getAgent().getMessages().isEmpty()) {
            return "✗ 没有需要保存的对话";
        }

        Path path = context.getSessionManager().saveSession(context.getAgent().getMessages());
        if (path != null) {
            return "✓ 会话已保存: " + path.getFileName();
        }
        return "✗ 保存失败";
    }
}

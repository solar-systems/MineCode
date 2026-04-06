package cn.abelib.minecode.skill.builtin;

import cn.abelib.minecode.skill.Skill;
import cn.abelib.minecode.skill.SkillContext;

import java.util.List;

/**
 * 加载会话技能
 *
 * @author Abel
 */
public class LoadSkill implements Skill {

    @Override
    public String name() {
        return "load";
    }

    @Override
    public String description() {
        return "加载指定会话";
    }

    @Override
    public String usage() {
        return "/load <session-id>";
    }

    @Override
    public boolean requiresArgs() {
        return true;
    }

    @Override
    public String execute(SkillContext context, String args) {
        if (args == null || args.isBlank()) {
            return "✗ 请指定会话 ID\n用法: /load <session-id>";
        }

        String sessionId = args.trim();
        List<?> messages = context.getSessionManager().loadSession(sessionId);

        if (messages == null) {
            return "✗ 会话不存在: " + sessionId;
        }

        context.getAgent().reset();
        for (Object msg : messages) {
            if (msg instanceof com.fasterxml.jackson.databind.node.ObjectNode node) {
                context.getAgent().getMessages().add(node);
            }
        }

        return String.format("✓ 会话已加载: %s (%d 条消息)", sessionId, messages.size());
    }
}

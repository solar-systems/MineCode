package cn.abelib.minecode.skill.builtin;

import cn.abelib.minecode.session.SessionInfo;
import cn.abelib.minecode.skill.Skill;
import cn.abelib.minecode.skill.SkillContext;

import java.util.List;

/**
 * 列出会话技能
 *
 * @author Abel
 */
public class SessionsSkill implements Skill {

    @Override
    public String name() {
        return "sessions";
    }

    @Override
    public String description() {
        return "列出所有会话";
    }

    @Override
    public String execute(SkillContext context, String args) {
        List<SessionInfo> sessions = context.getSessionManager().listSessions(20);

        if (sessions.isEmpty()) {
            return "没有保存的会话";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("最近的会话:\n\n");

        int i = 1;
        for (SessionInfo session : sessions) {
            sb.append(String.format("%2d. %s\n", i++, session.format()));
        }

        sb.append("\n使用 /load <id> 加载会话");

        return sb.toString();
    }
}

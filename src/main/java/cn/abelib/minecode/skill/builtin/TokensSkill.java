package cn.abelib.minecode.skill.builtin;

import cn.abelib.minecode.hook.BuiltinHooks;
import cn.abelib.minecode.skill.Skill;
import cn.abelib.minecode.skill.SkillContext;

/**
 * Token 统计技能
 *
 * @author Abel
 */
public class TokensSkill implements Skill {

    @Override
    public String name() {
        return "tokens";
    }

    @Override
    public String description() {
        return "显示 Token 使用统计";
    }

    @Override
    public String execute(SkillContext context, String args) {
        // 从 LLM 客户端获取统计
        long promptTokens = context.getLlmClient().getTotalPromptTokens();
        long completionTokens = context.getLlmClient().getTotalCompletionTokens();
        long total = promptTokens + completionTokens;

        int messageCount = context.getAgent().getMessages().size();

        return String.format("""
                ╔═══════════════════════════════════════╗
                ║           Token 使用统计               ║
                ╠═══════════════════════════════════════╣
                ║  Prompt Tokens:     %,15d  ║
                ║  Completion Tokens: %,15d  ║
                ║  ────────────────────────────────────  ║
                ║  总计:              %,15d  ║
                ║  消息数:            %,15d  ║
                ╚═══════════════════════════════════════╝
                """, promptTokens, completionTokens, total, messageCount);
    }
}

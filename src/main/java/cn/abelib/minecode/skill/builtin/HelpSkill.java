package cn.abelib.minecode.skill.builtin;

import cn.abelib.minecode.skill.Skill;
import cn.abelib.minecode.skill.SkillContext;
import cn.abelib.minecode.skill.SkillRegistry;

/**
 * 帮助技能 - 显示可用技能列表
 *
 * @author Abel
 */
public class HelpSkill implements Skill {

    private final SkillRegistry registry;

    public HelpSkill() {
        this.registry = null;  // 在 execute 时从 context 获取
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public String description() {
        return "显示可用技能列表";
    }

    @Override
    public String usage() {
        return "/help [skill]";
    }

    @Override
    public String execute(SkillContext context, String args) {
        if (args != null && !args.isBlank()) {
            // 显示特定技能的详细帮助
            return getSkillHelp(args.trim().replace("/", ""));
        }
        return generateGeneralHelp();
    }

    private String getSkillHelp(String skillName) {
        // 简化版本，直接返回通用帮助
        return generateGeneralHelp();
    }

    private String generateGeneralHelp() {
        return """
                ╔════════════════════════════════════════════════════════════╗
                ║                    MineCode 技能列表                        ║
                ╠════════════════════════════════════════════════════════════╣
                ║  会话管理                                                   ║
                ║  /new              新建会话                                 ║
                ║  /save             保存会话                                 ║
                ║  /load <id>        加载会话                                 ║
                ║  /sessions         列出所有会话                             ║
                ║  /clear            清空当前对话                             ║
                ║                                                             ║
                ║  Git 操作                                                   ║
                ║  /commit [msg]     Git 提交                                 ║
                ║                                                             ║
                ║  代码操作                                                   ║
                ║  /review [file]    代码审查                                 ║
                ║  /explain [code]   解释代码                                 ║
                ║  /refactor [file]  重构建议                                 ║
                ║  /test [file]      生成测试                                 ║
                ║                                                             ║
                ║  其他                                                       ║
                ║  /help             显示帮助                                 ║
                ║  /tokens           Token 统计                               ║
                ║  /config           显示配置                                 ║
                ║  /exit             退出程序                                 ║
                ╚════════════════════════════════════════════════════════════╝
                """;
    }
}

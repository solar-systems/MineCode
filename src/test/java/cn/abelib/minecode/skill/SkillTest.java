package cn.abelib.minecode.skill;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Skill 模块测试
 *
 * @author Abel
 */
class SkillTest {

    private SkillRegistry registry;
    private SkillContext context;

    @BeforeEach
    void setUp() {
        registry = new SkillRegistry();
        context = SkillContext.builder().build();
    }

    // ==================== Skill Interface Tests ====================

    @Test
    void testSkill_defaultMethods() {
        Skill skill = new TestSkill("test", "Test skill");

        assertEquals("/test", skill.usage());
        assertFalse(skill.requiresArgs());
        assertNull(skill.validateArgs("anything"));
    }

    @Test
    void testSkill_requiresArgs() {
        Skill skill = new TestSkill("test", "Test skill", true);

        assertTrue(skill.requiresArgs());
        assertNull(skill.validateArgs("has args"));
        assertEquals("用法: /test", skill.validateArgs(""));
        assertEquals("用法: /test", skill.validateArgs(null));
    }

    // ==================== SkillRegistry Tests ====================

    @Test
    void testSkillRegistry_defaultSkillsRegistered() {
        // 注册了 13 个默认技能
        assertTrue(registry.size() >= 13);
    }

    @Test
    void testSkillRegistry_register() {
        int initialSize = registry.size();

        registry.register(new TestSkill("custom", "Custom skill"));

        assertEquals(initialSize + 1, registry.size());
        assertTrue(registry.exists("custom"));
    }

    @Test
    void testSkillRegistry_register_caseInsensitive() {
        registry.register(new TestSkill("MySkill", "Test"));

        assertTrue(registry.exists("myskill"));
        assertTrue(registry.exists("MYSKILL"));
        assertTrue(registry.exists("MySkill"));
    }

    @Test
    void testSkillRegistry_register_overwrite() {
        registry.register(new TestSkill("test", "Original"));
        registry.register(new TestSkill("test", "Overwritten"));

        Skill skill = registry.get("test");
        assertEquals("Overwritten", skill.description());
    }

    @Test
    void testSkillRegistry_register_null() {
        int size = registry.size();

        registry.register(null);

        assertEquals(size, registry.size());
    }

    @Test
    void testSkillRegistry_unregister() {
        registry.register(new TestSkill("test", "Test"));

        Skill removed = registry.unregister("test");

        assertNotNull(removed);
        assertEquals("test", removed.name());
        assertFalse(registry.exists("test"));
    }

    @Test
    void testSkillRegistry_unregister_caseInsensitive() {
        registry.register(new TestSkill("MySkill", "Test"));

        assertNotNull(registry.unregister("MYSKILL"));
        assertFalse(registry.exists("myskill"));
    }

    @Test
    void testSkillRegistry_get() {
        registry.register(new TestSkill("test", "Test skill"));

        Skill skill = registry.get("test");

        assertNotNull(skill);
        assertEquals("test", skill.name());
    }

    @Test
    void testSkillRegistry_get_notFound() {
        assertNull(registry.get("nonexistent"));
    }

    @Test
    void testSkillRegistry_exists() {
        registry.register(new TestSkill("test", "Test"));

        assertTrue(registry.exists("test"));
        assertFalse(registry.exists("nonexistent"));
    }

    @Test
    void testSkillRegistry_execute() {
        registry.register(new TestSkill("echo", "Echo skill") {
            @Override
            public String execute(SkillContext context, String args) {
                return "Echo: " + args;
            }
        });

        String result = registry.execute("echo", context, "hello");

        assertEquals("Echo: hello", result);
    }

    @Test
    void testSkillRegistry_execute_notFound() {
        assertThrows(SkillRegistry.SkillNotFoundException.class,
                () -> registry.execute("nonexistent", context, ""));
    }

    @Test
    void testSkillRegistry_execute_validationFailed() {
        registry.register(new TestSkill("test", "Test", true));

        String result = registry.execute("test", context, "");

        assertEquals("用法: /test", result);
    }

    @Test
    void testSkillRegistry_execute_executionError() {
        registry.register(new TestSkill("error", "Error skill") {
            @Override
            public String execute(SkillContext context, String args) {
                throw new RuntimeException("Test error");
            }
        });

        assertThrows(SkillRegistry.SkillExecutionException.class,
                () -> registry.execute("error", context, ""));
    }

    @Test
    void testSkillRegistry_listAll() {
        List<Skill> skills = registry.listAll();

        assertNotNull(skills);
        assertFalse(skills.isEmpty());
        assertTrue(skills.size() >= 13);
    }

    @Test
    void testSkillRegistry_listNames() {
        List<String> names = registry.listNames();

        assertNotNull(names);
        assertFalse(names.isEmpty());
        assertTrue(names.contains("help"));
        assertTrue(names.contains("commit"));
    }

    @Test
    void testSkillRegistry_size() {
        int size = registry.size();

        assertTrue(size >= 13);
    }

    @Test
    void testSkillRegistry_generateHelpText() {
        String help = registry.generateHelpText();

        assertNotNull(help);
        assertTrue(help.contains("可用技能"));
        assertTrue(help.contains("/help"));
        assertTrue(help.contains("/commit"));
    }

    // ==================== Built-in Skills Tests ====================

    @Test
    void testHelpSkill() {
        Skill help = registry.get("help");

        assertNotNull(help);
        assertEquals("help", help.name());
        assertEquals("显示可用技能列表", help.description());
        assertFalse(help.requiresArgs());

        String result = help.execute(context, "");
        assertTrue(result.contains("技能列表") || result.contains("技能"));
    }

    @Test
    void testClearSkill() {
        Skill clear = registry.get("clear");

        assertNotNull(clear);
        assertEquals("clear", clear.name());
        assertEquals("清空当前对话", clear.description());

        // ClearSkill 需要 Agent，跳过执行测试
    }

    @Test
    void testTokensSkill() {
        Skill tokens = registry.get("tokens");

        assertNotNull(tokens);
        assertEquals("tokens", tokens.name());
        assertEquals("显示 Token 使用统计", tokens.description());
    }

    @Test
    void testNewSkill() {
        Skill newSkill = registry.get("new");

        assertNotNull(newSkill);
        assertEquals("new", newSkill.name());
        assertEquals("创建新会话", newSkill.description());
    }

    @Test
    void testSessionsSkill() {
        Skill sessions = registry.get("sessions");

        assertNotNull(sessions);
        assertEquals("sessions", sessions.name());
        assertEquals("列出所有会话", sessions.description());
    }

    @Test
    void testConfigSkill() {
        Skill config = registry.get("config");

        assertNotNull(config);
        assertEquals("config", config.name());
        assertEquals("显示当前配置", config.description());
    }

    @Test
    void testCommitSkill() {
        Skill commit = registry.get("commit");

        assertNotNull(commit);
        assertEquals("commit", commit.name());
        assertEquals("Git 提交代码", commit.description());
    }

    @Test
    void testReviewSkill() {
        Skill review = registry.get("review");

        assertNotNull(review);
        assertEquals("review", review.name());
        assertEquals("审查代码质量", review.description());
    }

    @Test
    void testExplainSkill() {
        Skill explain = registry.get("explain");

        assertNotNull(explain);
        assertEquals("explain", explain.name());
        assertEquals("解释代码逻辑", explain.description());
    }

    @Test
    void testRefactorSkill() {
        Skill refactor = registry.get("refactor");

        assertNotNull(refactor);
        assertEquals("refactor", refactor.name());
        assertEquals("重构建议", refactor.description());
    }

    @Test
    void testTestSkill() {
        Skill test = registry.get("test");

        assertNotNull(test);
        assertEquals("test", test.name());
        assertEquals("生成单元测试", test.description());
    }

    @Test
    void testSaveSkill() {
        Skill save = registry.get("save");

        assertNotNull(save);
        assertEquals("save", save.name());
        assertEquals("保存当前会话", save.description());
    }

    @Test
    void testLoadSkill() {
        Skill load = registry.get("load");

        assertNotNull(load);
        assertEquals("load", load.name());
        assertEquals("加载指定会话", load.description());
    }

    // ==================== SkillContext Tests ====================

    @Test
    void testSkillContext_builder() {
        SkillContext ctx = SkillContext.builder().build();

        assertNotNull(ctx);
    }

    @Test
    void testSkillContext_withAgent() {
        SkillContext ctx = SkillContext.builder()
                .agent(null)
                .llmClient(null)
                .sessionManager(null)
                .build();

        assertNotNull(ctx);
        assertNull(ctx.getAgent());
        assertNull(ctx.getLlmClient());
        assertNull(ctx.getSessionManager());
    }

    // ==================== Test Helper Classes ====================

    static class TestSkill implements Skill {
        private final String name;
        private final String description;
        private final boolean requiresArgs;

        TestSkill(String name, String description) {
            this(name, description, false);
        }

        TestSkill(String name, String description, boolean requiresArgs) {
            this.name = name;
            this.description = description;
            this.requiresArgs = requiresArgs;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public boolean requiresArgs() {
            return requiresArgs;
        }

        @Override
        public String execute(SkillContext context, String args) {
            return "Executed: " + name;
        }
    }
}

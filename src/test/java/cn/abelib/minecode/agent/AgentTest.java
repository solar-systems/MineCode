package cn.abelib.minecode.agent;

import cn.abelib.minecode.hook.Hook;
import cn.abelib.minecode.hook.HookBuilder;
import cn.abelib.minecode.llm.LLMClient;
import cn.abelib.minecode.llm.LLMConfig;
import cn.abelib.minecode.tools.ToolPreset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Agent 测试
 *
 * @author Abel
 */
class AgentTest {

    private LLMClient llmClient;
    private Agent.Builder builder;

    @BeforeEach
    void setUp() {
        // 创建真实的 LLM 客户端用于测试
        LLMConfig config = new LLMConfig();
        config.setApiKey("test-key");
        config.setModel("gpt-4");
        llmClient = new LLMClient(config);
        builder = Agent.builder().llm(llmClient);
    }

    // ==================== Builder Tests ====================

    @Test
    void testAgentBuilder_defaultValues() {
        Agent agent = builder.build();

        assertEquals("MineCode Agent", agent.getName());
        assertEquals("AI coding assistant", agent.getDescription());
        assertEquals(50, agent.getMaxRounds());
    }

    @Test
    void testAgentBuilder_customValues() {
        Agent agent = builder
                .name("CustomAgent")
                .description("A custom agent")
                .maxRounds(100)
                .build();

        assertEquals("CustomAgent", agent.getName());
        assertEquals("A custom agent", agent.getDescription());
        assertEquals(100, agent.getMaxRounds());
    }

    @Test
    void testAgentBuilder_withHooks() {
        Hook hook1 = HookBuilder.logging();
        Hook hook2 = HookBuilder.tokenStats();

        Agent agent = builder
                .hook(hook1)
                .hook(hook2)
                .build();

        assertEquals(2, agent.getHookManager().size());
    }

    @Test
    void testAgentBuilder_withHookList() {
        List<Hook> hooks = List.of(
                HookBuilder.logging(),
                HookBuilder.tokenStats()
        );

        Agent agent = builder
                .hooks(hooks)
                .build();

        assertEquals(2, agent.getHookManager().size());
    }

    @Test
    void testAgentBuilder_withToolPreset() {
        Agent agent = builder
                .toolPreset(ToolPreset.MINIMAL)
                .build();

        // 验证工具预设被应用
        var activeTools = agent.getActiveTools();
        assertNotNull(activeTools);
        // MINIMAL 只有搜索工具
        assertTrue(activeTools.stream().anyMatch(t -> t.getName().equals("glob")));
        assertTrue(activeTools.stream().anyMatch(t -> t.getName().equals("grep")));
        // 不应该有 bash
        assertFalse(activeTools.stream().anyMatch(t -> t.getName().equals("bash")));
    }

    // ==================== Agent Operations Tests ====================

    @Test
    void testAgent_reset() {
        Agent agent = builder.build();

        // 模拟添加消息
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        agent.getMessages().add(mapper.createObjectNode());

        assertEquals(1, agent.getMessages().size());

        agent.reset();

        assertEquals(0, agent.getMessages().size());
    }

    @Test
    void testAgent_addHook() {
        Agent agent = builder.build();
        int initialSize = agent.getHookManager().size();

        agent.addHook(HookBuilder.timing());

        assertEquals(initialSize + 1, agent.getHookManager().size());
    }

    @Test
    void testAgent_removeHook() {
        Hook hook = HookBuilder.timing();
        Agent agent = builder.hook(hook).build();

        assertTrue(agent.getHookManager().getHooks().contains(hook));

        agent.removeHook(hook);

        assertFalse(agent.getHookManager().getHooks().contains(hook));
    }

    // ==================== Tool Group Management Tests ====================

    @Test
    void testAgent_activateToolGroup() {
        Agent agent = builder.build();

        // 激活工具组
        agent.activateToolGroup("execute");

        // 验证方法调用成功（不抛异常）
        assertNotNull(agent.getToolGroupManager());
    }

    @Test
    void testAgent_deactivateToolGroup() {
        Agent agent = builder.build();

        // 禁用工具组
        agent.deactivateToolGroup("execute");

        // 验证方法调用成功
        assertNotNull(agent.getToolGroupManager());
    }

    @Test
    void testAgent_applyToolPreset() {
        Agent agent = builder.build();

        // 应用 FULL_ACCESS 预设
        agent.applyToolPreset(ToolPreset.FULL_ACCESS);

        // 验证工具状态
        var activeTools = agent.getToolGroupManager().getActiveTools();
        assertTrue(activeTools.stream().anyMatch(t -> t.getName().equals("read_file")));
        assertTrue(activeTools.stream().anyMatch(t -> t.getName().equals("bash")));
    }

    // ==================== Interrupt Tests ====================

    @Test
    void testAgent_interrupt() {
        Agent agent = builder.build();

        assertFalse(agent.getInterruptContext().isInterrupted());

        agent.interrupt("Test interrupt");

        assertTrue(agent.getInterruptContext().isInterrupted());
        assertEquals("Test interrupt", agent.getInterruptContext().getInterruptMessage());
    }

    @Test
    void testAgent_clearInterrupt() {
        Agent agent = builder.build();
        agent.interrupt("Test");

        agent.clearInterrupt();

        assertFalse(agent.getInterruptContext().isInterrupted());
    }

    // ==================== Getters Tests ====================

    @Test
    void testAgent_getTools() {
        Agent agent = builder.build();

        var tools = agent.getTools();

        assertNotNull(tools);
    }

    @Test
    void testAgent_getHookManager() {
        Agent agent = builder.build();

        assertNotNull(agent.getHookManager());
    }

    @Test
    void testAgent_getToolGroupManager() {
        Agent agent = builder.build();

        assertNotNull(agent.getToolGroupManager());
    }

    // ==================== Close Tests ====================

    @Test
    void testAgent_close() {
        Agent agent = builder.build();

        // 关闭不应该抛出异常
        assertDoesNotThrow(agent::close);
    }

    // ==================== Plan Mode Tests ====================

    @Test
    void testAgent_planNotebook_initiallyNull() {
        Agent agent = builder.build();

        assertNull(agent.getPlanNotebook());
    }

    @Test
    void testAgent_clearPlan() {
        Agent agent = builder.build();

        // 清空空计划不应该抛出异常
        assertDoesNotThrow(agent::clearPlan);
    }
}

package cn.abelib.minecode.integration;

import cn.abelib.minecode.agent.Agent;
import cn.abelib.minecode.hook.HookBuilder;
import cn.abelib.minecode.hook.Hook;
import cn.abelib.minecode.llm.LLMClient;
import cn.abelib.minecode.llm.LLMConfig;
import cn.abelib.minecode.permission.PermissionHook;
import cn.abelib.minecode.permission.PermissionManager;
import cn.abelib.minecode.permission.PermissionPreset;
import cn.abelib.minecode.tools.ToolPreset;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Agent 集成测试
 *
 * <p>测试 Agent 与各组件的集成：
 * <ul>
 *   <li>Agent + Hook 系统</li>
 *   <li>Agent + Permission 系统</li>
 *   <li>Agent + Tool 系统</li>
 * </ul>
 *
 * @author Abel
 */
class AgentIntegrationTest {

    private LLMClient llmClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        LLMConfig config = new LLMConfig();
        config.setApiKey("test-key");
        config.setModel("gpt-4");
        llmClient = new LLMClient(config);
    }

    // ==================== Agent + Hook Integration ====================

    @Test
    void testAgent_withMultipleHooks() {
        var tokenHook = HookBuilder.tokenStats();
        var timingHook = HookBuilder.timing();
        var loggingHook = HookBuilder.logging();

        Agent agent = Agent.builder()
                .llm(llmClient)
                .hook(tokenHook)
                .hook(timingHook)
                .hook(loggingHook)
                .build();

        assertEquals(3, agent.getHookManager().size());
    }

    @Test
    void testAgent_hookPriorityOrder() {
        // 创建不同优先级的 Hook
        var lowPriority = HookBuilder.custom("Low", e -> {});
        var highPriority = HookBuilder.withPriority(10, HookBuilder.custom("High", e -> {}));

        Agent agent = Agent.builder()
                .llm(llmClient)
                .hook(lowPriority)
                .hook(highPriority)
                .build();

        var hooks = agent.getHookManager().getHooks();
        // 高优先级（数值小）应该在前面
        assertEquals("High", hooks.get(0).name());
        assertEquals("Low", hooks.get(1).name());
    }

    // ==================== Agent + Permission Integration ====================

    @Test
    void testAgent_withPermissionHook() {
        PermissionManager permManager = PermissionManager.builder()
                .preset(PermissionPreset.READ_ONLY)
                .build();

        PermissionHook permHook = new PermissionHook(permManager);

        Agent agent = Agent.builder()
                .llm(llmClient)
                .hook(permHook)
                .build();

        // 验证 Permission Hook 已注册
        assertTrue(agent.getHookManager().getHooks().stream()
                .anyMatch(h -> h.name().equals("PermissionHook")));
    }

    @Test
    void testAgent_withPermissionPreset() {
        Hook permissionHook = HookBuilder.permission(PermissionPreset.STANDARD);

        Agent agent = Agent.builder()
                .llm(llmClient)
                .hook(permissionHook)
                .build();

        assertNotNull(agent);
    }

    @Test
    void testAgent_withCustomPermission() {
        Hook permissionHook = HookBuilder.permission(builder -> builder
                .allow("Read")
                .allow("Glob")
                .deny("Bash(rm *)")
                .askUser("Bash(sudo *)")
        );

        Agent agent = Agent.builder()
                .llm(llmClient)
                .hook(permissionHook)
                .build();

        assertNotNull(agent);
    }

    // ==================== Agent + Tool Integration ====================

    @Test
    void testAgent_withToolPreset() {
        Agent agent = Agent.builder()
                .llm(llmClient)
                .toolPreset(ToolPreset.FULL_ACCESS)
                .build();

        var tools = agent.getActiveTools();

        // FULL_ACCESS 应该有所有工具
        assertTrue(tools.stream().anyMatch(t -> t.getName().equals("read_file")));
        assertTrue(tools.stream().anyMatch(t -> t.getName().equals("glob")));
        assertTrue(tools.stream().anyMatch(t -> t.getName().equals("grep")));
        assertTrue(tools.stream().anyMatch(t -> t.getName().equals("bash")));
    }

    @Test
    void testAgent_toolGroupManagement() {
        Agent agent = Agent.builder()
                .llm(llmClient)
                .build();

        // 禁用 execute 工具组
        agent.deactivateToolGroup("execute");

        // 重新激活
        agent.activateToolGroup("execute");

        assertNotNull(agent.getToolGroupManager());
    }

    // ==================== Tool Schema Tests ====================

    @Test
    void testAgent_toolSchemas() {
        Agent agent = Agent.builder()
                .llm(llmClient)
                .build();

        var schemas = agent.getToolGroupManager().getActiveToolSchemas();

        assertNotNull(schemas);

        // 每个 schema 应该有正确的结构
        for (ObjectNode schema : schemas) {
            assertTrue(schema.has("function"));
            assertTrue(schema.get("function").has("name"));
            assertTrue(schema.get("function").has("description"));
            assertTrue(schema.get("function").has("parameters"));
        }
    }

    // ==================== Hook Chain Tests ====================

    @Test
    void testAgent_hookChain() {
        StringBuilder sb = new StringBuilder();

        Hook chain = HookBuilder.chain(
                HookBuilder.custom("H1", e -> sb.append("1")),
                HookBuilder.custom("H2", e -> sb.append("2")),
                HookBuilder.custom("H3", e -> sb.append("3"))
        );

        Agent agent = Agent.builder()
                .llm(llmClient)
                .hook(chain)
                .build();

        // 验证 chain hook 已注册
        assertNotNull(agent);
        assertEquals(1, agent.getHookManager().size());
    }

    // ==================== Interrupt Control Tests ====================

    @Test
    void testAgent_interruptControl() {
        Agent agent = Agent.builder()
                .llm(llmClient)
                .build();

        // 初始状态：未中断
        assertFalse(agent.getInterruptContext().isInterrupted());

        // 发送中断
        agent.interrupt("User requested stop");

        // 验证中断状态
        assertTrue(agent.getInterruptContext().isInterrupted());
        assertEquals("User requested stop", agent.getInterruptContext().getInterruptMessage());

        // 清除中断
        agent.clearInterrupt();
        assertFalse(agent.getInterruptContext().isInterrupted());
    }

    // ==================== Configuration Tests ====================

    @Test
    void testAgent_maxRounds() {
        Agent agent = Agent.builder()
                .llm(llmClient)
                .maxRounds(100)
                .build();

        assertEquals(100, agent.getMaxRounds());
    }

    @Test
    void testAgent_maxContextTokens() {
        // 这个参数影响上下文管理
        Agent agent = Agent.builder()
                .llm(llmClient)
                .maxContextTokens(200_000)
                .build();

        assertNotNull(agent);
    }

    // ==================== Close Tests ====================

    @Test
    void testAgent_close() {
        Agent agent = Agent.builder()
                .llm(llmClient)
                .build();

        // 关闭不应该抛出异常
        assertDoesNotThrow(agent::close);
    }
}

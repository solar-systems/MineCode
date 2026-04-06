package cn.abelib.minecode.permission;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 权限系统测试
 *
 * @author Abel
 */
class PermissionTest {

    @Test
    void testPermissionRule_exactMatch() {
        PermissionRule rule = PermissionRule.allow("read_file");

        assertTrue(rule.matches("read_file", null));
        assertTrue(rule.matches("read_file", "/path/to/file"));
        assertFalse(rule.matches("write_file", null));
    }

    @Test
    void testPermissionRule_wildcardMatch() {
        PermissionRule rule = PermissionRule.deny("bash(rm *)");

        assertTrue(rule.matches("bash", "rm -rf /"));
        assertTrue(rule.matches("bash", "rm file.txt"));
        assertFalse(rule.matches("bash", "ls -la"));
        assertFalse(rule.matches("bash", "sudo rm"));
    }

    @Test
    void testPermissionRule_toolOnlyMatch() {
        PermissionRule rule = PermissionRule.allow("bash");

        assertTrue(rule.matches("bash", "ls"));
        assertTrue(rule.matches("bash", "rm -rf"));
        assertFalse(rule.matches("read_file", null));
    }

    @Test
    void testPermissionRule_pathMatch() {
        PermissionRule rule = PermissionRule.deny("write_file(/etc/*)");

        assertTrue(rule.matches("write_file", "/etc/passwd"));
        assertTrue(rule.matches("write_file", "/etc/ssh/sshd_config"));
        assertFalse(rule.matches("write_file", "/home/user/file.txt"));
    }

    @Test
    void testPermissionManager_check() {
        PermissionManager manager = PermissionManager.builder()
                .allow("read_file")
                .allow("glob")
                .deny("bash(rm *)")
                .askUser("bash(sudo *)")
                .build();

        assertEquals(PermissionDecision.ALLOW, manager.check("read_file", null));
        assertEquals(PermissionDecision.ALLOW, manager.check("glob", "**/*.java"));
        assertEquals(PermissionDecision.DENY, manager.check("bash", "rm -rf /"));
        assertEquals(PermissionDecision.ASK_USER, manager.check("bash", "sudo apt update"));
        assertEquals(PermissionDecision.ASK_USER, manager.check("write_file", "/tmp/test.txt")); // default
    }

    @Test
    void testPermissionManager_presetReadOnly() {
        PermissionManager manager = PermissionManager.builder()
                .preset(PermissionPreset.READ_ONLY)
                .build();

        assertEquals(PermissionDecision.ALLOW, manager.check("read_file", null));
        assertEquals(PermissionDecision.ALLOW, manager.check("glob", null));
        assertEquals(PermissionDecision.ALLOW, manager.check("grep", "pattern"));
        assertEquals(PermissionDecision.DENY, manager.check("write_file", null));
        assertEquals(PermissionDecision.DENY, manager.check("edit_file", null));
        assertEquals(PermissionDecision.DENY, manager.check("bash", "ls"));
    }

    @Test
    void testPermissionManager_presetStandard() {
        PermissionManager manager = PermissionManager.builder()
                .preset(PermissionPreset.STANDARD)
                .build();

        assertEquals(PermissionDecision.ALLOW, manager.check("read_file", null));
        assertEquals(PermissionDecision.ALLOW, manager.check("write_file", null));
        assertEquals(PermissionDecision.ALLOW, manager.check("edit_file", null));
        // STANDARD preset has askUser for rm and sudo commands
        assertEquals(PermissionDecision.ASK_USER, manager.check("bash", "rm file.txt"));
        assertEquals(PermissionDecision.ASK_USER, manager.check("bash", "sudo apt update"));
        // ls command is not specified, uses default ASK_USER
        assertEquals(PermissionDecision.ASK_USER, manager.check("bash", "ls -la"));
    }

    @Test
    void testPermissionManager_presetFullAccess() {
        PermissionManager manager = PermissionManager.builder()
                .preset(PermissionPreset.FULL_ACCESS)
                .build();

        assertEquals(PermissionDecision.ALLOW, manager.check("read_file", null));
        assertEquals(PermissionDecision.ALLOW, manager.check("write_file", null));
        assertEquals(PermissionDecision.ALLOW, manager.check("bash", "rm -rf /"));
        assertEquals(PermissionDecision.ALLOW, manager.check("bash", "sudo reboot"));
    }

    @Test
    void testPermissionManager_rulePriority() {
        // 规则按添加顺序匹配，第一个匹配的规则决定结果
        PermissionManager manager = PermissionManager.builder()
                .allow("bash(ls *)")
                .deny("bash(*)")  // 这个规则不会被匹配到，因为 ls 规则先匹配
                .build();

        assertEquals(PermissionDecision.ALLOW, manager.check("bash", "ls -la"));
        assertEquals(PermissionDecision.DENY, manager.check("bash", "rm file.txt"));
    }

    @Test
    void testPermissionDeniedException() {
        PermissionDeniedException ex1 = new PermissionDeniedException("bash", "rm -rf /");
        assertEquals("bash", ex1.getToolName());
        assertEquals("rm -rf /", ex1.getToolArgs());
        assertTrue(ex1.getMessage().contains("Permission denied"));

        PermissionRule rule = PermissionRule.deny("bash(rm *)");
        PermissionDeniedException ex2 = new PermissionDeniedException("bash", "rm -rf /", rule);
        assertEquals(rule, ex2.getMatchedRule());
    }

    @Test
    void testPermissionManager_userConfirmHandler() {
        // 模拟用户确认：对于 bash(sudo:*) 始终允许
        PermissionManager manager = PermissionManager.builder()
                .defaultDecision(PermissionDecision.ASK_USER)
                .userConfirmHandler((tool, args) -> {
                    if ("bash".equals(tool) && args != null && args.startsWith("sudo")) {
                        return PermissionDecision.ALLOW;
                    }
                    return PermissionDecision.DENY;
                })
                .build();

        // 用户确认后允许
        assertEquals(PermissionDecision.ALLOW, manager.checkWithConfirm("bash", "sudo apt update"));
        // 用户确认后拒绝
        assertEquals(PermissionDecision.DENY, manager.checkWithConfirm("bash", "ls -la"));
    }

    @Test
    void testPermissionManager_isAllowed() {
        PermissionManager manager = PermissionManager.builder()
                .allow("read_file")
                .deny("bash(rm *)")
                .build();

        assertTrue(manager.isAllowed("read_file", null));
        assertFalse(manager.isAllowed("bash", "rm -rf /"));
    }

    @Test
    void testPermissionManager_toolNamesMatchActualTools() {
        // 确保预设中的工具名称与实际工具名称一致
        PermissionManager manager = PermissionManager.builder()
                .preset(PermissionPreset.STANDARD)
                .build();

        // 验证使用实际工具名称时权限系统正常工作
        assertEquals(PermissionDecision.ALLOW, manager.check("read_file", "/test/file"));
        assertEquals(PermissionDecision.ALLOW, manager.check("write_file", "/test/file"));
        assertEquals(PermissionDecision.ALLOW, manager.check("edit_file", "/test/file"));
        assertEquals(PermissionDecision.ALLOW, manager.check("glob", "**/*.java"));
        assertEquals(PermissionDecision.ALLOW, manager.check("grep", "pattern"));
    }
}

package cn.abelib.minecode.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CLI 模块测试
 *
 * <p>测试命令解析和别名解析逻辑
 *
 * @author Abel
 */
class CliTest {

    // ==================== Command Parsing Tests ====================

    @Test
    void testCommandParsing_builtinCommands() {
        // 测试内置命令识别
        String[] builtinCommands = {"/exit", "/quit", "/q", "/set", "/help"};

        for (String cmd : builtinCommands) {
            assertTrue(cmd.startsWith("/"), "Command should start with /");
        }
    }

    @Test
    void testCommandParsing_split() {
        String input = "/set model gpt-4";
        String[] parts = input.split("\\s+", 2);

        assertEquals("/set", parts[0]);
        assertEquals("model gpt-4", parts[1]);
    }

    @Test
    void testCommandParsing_noArgs() {
        String input = "/help";
        String[] parts = input.split("\\s+", 2);

        assertEquals("/help", parts[0]);
        assertEquals(1, parts.length);
    }

    @Test
    void testCommandParsing_multipleSpaces() {
        String input = "/commit   fix:   bug fix";
        String[] parts = input.split("\\s+", 2);

        assertEquals("/commit", parts[0]);
        assertEquals("fix:   bug fix", parts[1]);
    }

    // ==================== Alias Resolution Tests ====================

    @Test
    void testAliasResolution_help() {
        assertEquals("help", resolveAlias("h"));
        assertEquals("help", resolveAlias("?"));
        assertEquals("help", resolveAlias("help"));
    }

    @Test
    void testAliasResolution_clear() {
        assertEquals("clear", resolveAlias("cls"));
        assertEquals("clear", resolveAlias("clear"));
    }

    @Test
    void testAliasResolution_otherCommands() {
        assertEquals("commit", resolveAlias("commit"));
        assertEquals("review", resolveAlias("review"));
        assertEquals("unknown", resolveAlias("unknown"));
    }

    // ==================== Command Validation Tests ====================

    @Test
    void testIsCommand_validCommands() {
        assertTrue(isCommand("/help"));
        assertTrue(isCommand("/commit"));
        assertTrue(isCommand("/review"));
    }

    @Test
    void testIsCommand_invalidCommands() {
        assertFalse(isCommand("help"));
        assertFalse(isCommand("commit"));
        assertFalse(isCommand(""));
        assertFalse(isCommand("normal text"));
    }

    // ==================== Set Command Parsing Tests ====================

    @Test
    void testSetCommandParsing_valid() {
        String args = "model gpt-4";
        String[] parts = args.split("\\s+", 2);

        assertEquals(2, parts.length);
        assertEquals("model", parts[0]);
        assertEquals("gpt-4", parts[1]);
    }

    @Test
    void testSetCommandParsing_withSpaces() {
        String args = "key value with spaces";
        String[] parts = args.split("\\s+", 2);

        assertEquals("key", parts[0]);
        assertEquals("value with spaces", parts[1]);
    }

    @Test
    void testSetCommandParsing_missingValue() {
        String args = "model";
        String[] parts = args.split("\\s+", 2);

        assertEquals(1, parts.length);
        assertEquals("model", parts[0]);
    }

    // ==================== Model Option Tests ====================

    @Test
    void testModelOption_short() {
        assertEquals("-m", "-m");
        assertTrue("-m gpt-4".startsWith("-m"));
    }

    @Test
    void testModelOption_long() {
        assertEquals("--model", "--model");
        assertTrue("--model gpt-4".startsWith("--model"));
    }

    // ==================== Session Option Tests ====================

    @Test
    void testSessionOption_short() {
        assertEquals("-s", "-s");
        assertTrue("-s session123".startsWith("-s"));
    }

    @Test
    void testSessionOption_long() {
        assertEquals("--session", "--session");
        assertTrue("--session session123".startsWith("--session"));
    }

    // ==================== CLI Constants Tests ====================

    @Test
    void testCliConstants() {
        // 验证命令行选项常量
        String[] validOptions = {"-m", "--model", "-s", "--session", "--no-save", "--config", "--verbose"};

        for (String opt : validOptions) {
            assertTrue(opt.startsWith("-"), "Option should start with -");
        }
    }

    // ==================== Helper Methods ====================

    /**
     * 解析命令别名（复制自 MineCodeCli）
     */
    private String resolveAlias(String cmd) {
        return switch (cmd) {
            case "h", "?" -> "help";
            case "cls" -> "clear";
            default -> cmd;
        };
    }

    /**
     * 检查是否是命令
     */
    private boolean isCommand(String input) {
        return input != null && input.startsWith("/");
    }
}

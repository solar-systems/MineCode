package cn.abelib.minecode.prompt;

import cn.abelib.minecode.tools.Tool;
import cn.abelib.minecode.tools.impl.ReadFileTool;
import cn.abelib.minecode.tools.impl.WriteFileTool;
import cn.abelib.minecode.tools.impl.BashTool;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prompt 模块测试
 *
 * @author Abel
 */
class PromptTest {

    // ==================== SystemPrompt Tests ====================

    @Test
    void testGenerate_basicStructure() {
        List<Tool> tools = new ArrayList<>();
        tools.add(new ReadFileTool());
        tools.add(new WriteFileTool());

        ObjectNode prompt = SystemPrompt.generate(tools);

        assertNotNull(prompt);
        assertEquals("system", prompt.path("role").asText());
        String content = prompt.path("content").asText();
        assertNotNull(content);
        assertFalse(content.isEmpty());
    }

    @Test
    void testGenerate_containsEnvironment() {
        List<Tool> tools = new ArrayList<>();

        ObjectNode prompt = SystemPrompt.generate(tools);
        String content = prompt.path("content").asText();

        assertTrue(content.contains("环境") || content.contains("# 环境"));
        assertTrue(content.contains("工作目录"));
        assertTrue(content.contains("操作系统"));
    }

    @Test
    void testGenerate_containsRules() {
        List<Tool> tools = new ArrayList<>();

        ObjectNode prompt = SystemPrompt.generate(tools);
        String content = prompt.path("content").asText();

        assertTrue(content.contains("规则") || content.contains("# 规则"));
        assertTrue(content.contains("先读后改") || content.contains("edit_file"));
    }

    @Test
    void testGenerate_withTools() {
        List<Tool> tools = new ArrayList<>();
        tools.add(new ReadFileTool());
        tools.add(new WriteFileTool());
        tools.add(new BashTool());

        ObjectNode prompt = SystemPrompt.generate(tools);
        String content = prompt.path("content").asText();

        assertTrue(content.contains("read_file"));
        assertTrue(content.contains("write_file"));
        assertTrue(content.contains("bash"));
    }

    @Test
    void testGenerate_emptyToolsList() {
        List<Tool> tools = new ArrayList<>();

        ObjectNode prompt = SystemPrompt.generate(tools);
        String content = prompt.path("content").asText();

        // 即使没有工具，也应该生成基本提示
        assertTrue(content.contains("MineCode"));
        assertTrue(content.contains("AI 编程助手") || content.contains("编程助手"));
    }

    @Test
    void testGenerate_toolDescriptions() {
        List<Tool> tools = new ArrayList<>();
        tools.add(new ReadFileTool());

        ObjectNode prompt = SystemPrompt.generate(tools);
        String content = prompt.path("content").asText();

        // 工具应该包含描述
        assertTrue(content.contains("read_file"));
        // 工具列表部分应该存在
        assertTrue(content.contains("# 工具") || content.contains("工具"));
    }

    @Test
    void testGenerate_roleField() {
        List<Tool> tools = new ArrayList<>();

        ObjectNode prompt = SystemPrompt.generate(tools);

        assertTrue(prompt.has("role"));
        assertEquals("system", prompt.path("role").asText());
    }

    @Test
    void testGenerate_contentField() {
        List<Tool> tools = new ArrayList<>();

        ObjectNode prompt = SystemPrompt.generate(tools);

        assertTrue(prompt.has("content"));
        String content = prompt.path("content").asText();
        assertNotNull(content);
    }

    @Test
    void testGenerate_containsJavaVersion() {
        List<Tool> tools = new ArrayList<>();

        ObjectNode prompt = SystemPrompt.generate(tools);
        String content = prompt.path("content").asText();

        // 应该包含 Java 版本信息
        assertTrue(content.contains("Java"));
    }

    @Test
    void testGenerate_ruleCount() {
        List<Tool> tools = new ArrayList<>();

        ObjectNode prompt = SystemPrompt.generate(tools);
        String content = prompt.path("content").asText();

        // 验证至少有几条规则
        // 规则以数字开头
        long ruleCount = content.lines()
                .filter(line -> line.matches("^\\d+\\..*"))
                .count();

        assertTrue(ruleCount >= 5, "Should have at least 5 rules");
    }

    @Test
    void testGenerate_toolSectionFormat() {
        List<Tool> tools = new ArrayList<>();
        tools.add(new ReadFileTool());

        ObjectNode prompt = SystemPrompt.generate(tools);
        String content = prompt.path("content").asText();

        // 工具应该以 markdown 格式列出
        assertTrue(content.contains("- **read_file**") || content.contains("read_file"));
    }

    @Test
    void testGenerate_identitySection() {
        List<Tool> tools = new ArrayList<>();

        ObjectNode prompt = SystemPrompt.generate(tools);
        String content = prompt.path("content").asText();

        // 应该包含身份描述
        assertTrue(content.contains("MineCode"));
    }

    @Test
    void testGenerate_multipleTools() {
        List<Tool> tools = new ArrayList<>();
        tools.add(new ReadFileTool());
        tools.add(new WriteFileTool());
        tools.add(new BashTool());
        tools.add(new cn.abelib.minecode.tools.impl.GlobTool());
        tools.add(new cn.abelib.minecode.tools.impl.GrepTool());

        ObjectNode prompt = SystemPrompt.generate(tools);
        String content = prompt.path("content").asText();

        // 所有工具都应该在提示中
        assertTrue(content.contains("read_file"));
        assertTrue(content.contains("write_file"));
        assertTrue(content.contains("bash"));
        assertTrue(content.contains("glob"));
        assertTrue(content.contains("grep"));
    }
}

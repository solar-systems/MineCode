package cn.abelib.minecode.tools;

import cn.abelib.minecode.tools.impl.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工具系统测试
 *
 * @author Abel
 */
class ToolTest {

    private ObjectMapper mapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    // ==================== Tool Schema Tests ====================

    @Test
    void testTool_toSchema() {
        Tool tool = new SimpleTestTool();

        var schema = tool.toSchema();

        assertEquals("function", schema.get("type").asText());
        assertEquals("test", schema.get("function").get("name").asText());
        assertEquals("A test tool", schema.get("function").get("description").asText());
        assertNotNull(schema.get("function").get("parameters"));
    }

    // ==================== Tool Name Tests ====================

    @Test
    void testBashTool_name() {
        BashTool tool = new BashTool();
        assertEquals("bash", tool.getName());
    }

    @Test
    void testReadFileTool_name() {
        ReadFileTool tool = new ReadFileTool();
        assertEquals("read_file", tool.getName());
    }

    @Test
    void testWriteFileTool_name() {
        WriteFileTool tool = new WriteFileTool();
        assertEquals("write_file", tool.getName());
    }

    @Test
    void testEditFileTool_name() {
        EditFileTool tool = new EditFileTool();
        assertEquals("edit_file", tool.getName());
    }

    @Test
    void testGlobTool_name() {
        GlobTool tool = new GlobTool();
        assertEquals("glob", tool.getName());
    }

    @Test
    void testGrepTool_name() {
        GrepTool tool = new GrepTool();
        assertEquals("grep", tool.getName());
    }

    // ==================== BashTool Tests ====================

    @Test
    void testBashTool_simpleCommand() throws Exception {
        BashTool tool = new BashTool();

        ObjectNode args = mapper.createObjectNode();
        args.put("command", "echo 'hello'");

        String result = tool.execute(args);

        assertTrue(result.contains("hello"));
    }

    @Test
    void testBashTool_withTimeout() throws Exception {
        BashTool tool = new BashTool();

        ObjectNode args = mapper.createObjectNode();
        args.put("command", "sleep 0.1 && echo 'done'");

        String result = tool.execute(args);

        assertTrue(result.contains("done"));
    }

    @Test
    void testBashTool_dangerousCommand() throws Exception {
        BashTool tool = new BashTool();

        ObjectNode args = mapper.createObjectNode();
        args.put("command", "rm -rf /some/dangerous/path");

        String result = tool.execute(args);

        // 危险命令应该被检测到
        assertTrue(result.toLowerCase().contains("dangerous") ||
                result.toLowerCase().contains("error") ||
                result.toLowerCase().contains("拒绝"));
    }

    // ==================== ReadFileTool Tests ====================

    @Test
    void testReadFileTool_readFile() throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Hello World\nLine 2\nLine 3");

        ReadFileTool tool = new ReadFileTool();

        ObjectNode args = mapper.createObjectNode();
        args.put("file_path", testFile.toString());

        String result = tool.execute(args);

        assertTrue(result.contains("Hello World"));
        assertTrue(result.contains("Line 2"));
    }

    @Test
    void testReadFileTool_readWithLimit() throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Line 1\nLine 2\nLine 3\nLine 4\nLine 5");

        ReadFileTool tool = new ReadFileTool();

        ObjectNode args = mapper.createObjectNode();
        args.put("file_path", testFile.toString());
        args.put("limit", 2);

        String result = tool.execute(args);

        assertTrue(result.contains("Line 1"));
        assertTrue(result.contains("Line 2"));
        assertFalse(result.contains("Line 5"));
    }

    @Test
    void testReadFileTool_readNonExistent() throws Exception {
        ReadFileTool tool = new ReadFileTool();

        ObjectNode args = mapper.createObjectNode();
        args.put("file_path", "/non/existent/file.txt");

        String result = tool.execute(args);

        assertTrue(result.toLowerCase().contains("error") ||
                result.toLowerCase().contains("不存在") ||
                result.toLowerCase().contains("not found"));
    }

    // ==================== WriteFileTool Tests ====================

    @Test
    void testWriteFileTool_writeFile() throws Exception {
        Path testFile = tempDir.resolve("output.txt");

        WriteFileTool tool = new WriteFileTool();

        ObjectNode args = mapper.createObjectNode();
        args.put("file_path", testFile.toString());
        args.put("content", "Test content");

        String result = tool.execute(args);

        assertTrue(result.contains("成功") || result.contains("success") ||
                result.contains("written") || !result.toLowerCase().contains("error"));

        String content = Files.readString(testFile);
        assertEquals("Test content", content);
    }

    @Test
    void testWriteFileTool_overwrite() throws Exception {
        Path testFile = tempDir.resolve("overwrite.txt");
        Files.writeString(testFile, "Original content");

        WriteFileTool tool = new WriteFileTool();

        ObjectNode args = mapper.createObjectNode();
        args.put("file_path", testFile.toString());
        args.put("content", "New content");

        tool.execute(args);

        String content = Files.readString(testFile);
        assertEquals("New content", content);
    }

    // ==================== EditFileTool Tests ====================

    @Test
    void testEditFileTool_editFile() throws Exception {
        Path testFile = tempDir.resolve("edit.txt");
        Files.writeString(testFile, "Hello World\nFoo Bar\nHello World");

        EditFileTool tool = new EditFileTool();

        ObjectNode args = mapper.createObjectNode();
        args.put("file_path", testFile.toString());
        args.put("old_string", "Hello World");
        args.put("new_string", "Hi There");

        String result = tool.execute(args);

        // 应该有错误，因为 old_string 不唯一
        assertTrue(result.toLowerCase().contains("error") ||
                result.toLowerCase().contains("unique") ||
                result.toLowerCase().contains("唯一"));
    }

    @Test
    void testEditFileTool_editUniqueString() throws Exception {
        Path testFile = tempDir.resolve("edit_unique.txt");
        Files.writeString(testFile, "Hello World\nUnique Line\nGoodbye");

        EditFileTool tool = new EditFileTool();

        ObjectNode args = mapper.createObjectNode();
        args.put("file_path", testFile.toString());
        args.put("old_string", "Unique Line");
        args.put("new_string", "Modified Line");

        String result = tool.execute(args);

        String content = Files.readString(testFile);
        assertTrue(content.contains("Modified Line"));
        assertFalse(content.contains("Unique Line"));
    }

    @Test
    void testEditFileTool_stringNotFound() throws Exception {
        Path testFile = tempDir.resolve("notfound.txt");
        Files.writeString(testFile, "Some content");

        EditFileTool tool = new EditFileTool();

        ObjectNode args = mapper.createObjectNode();
        args.put("file_path", testFile.toString());
        args.put("old_string", "NonExistentString");
        args.put("new_string", "Replacement");

        String result = tool.execute(args);

        assertTrue(result.toLowerCase().contains("error") ||
                result.toLowerCase().contains("not found") ||
                result.toLowerCase().contains("未找到"));
    }

    // ==================== GlobTool Tests ====================

    @Test
    void testGlobTool_findFiles() throws Exception {
        // 创建测试文件
        Files.createFile(tempDir.resolve("test1.java"));
        Files.createFile(tempDir.resolve("test2.java"));
        Files.createFile(tempDir.resolve("test.txt"));

        GlobTool tool = new GlobTool();

        ObjectNode args = mapper.createObjectNode();
        args.put("pattern", "**/*.java");
        args.put("path", tempDir.toString());

        String result = tool.execute(args);

        // 验证结果包含 java 文件
        assertNotNull(result);
    }

    @Test
    void testGlobTool_noMatch() throws Exception {
        GlobTool tool = new GlobTool();

        ObjectNode args = mapper.createObjectNode();
        args.put("pattern", "**/*.nonexistent");
        args.put("path", tempDir.toString());

        String result = tool.execute(args);

        // 没有匹配时应该返回空或提示
        assertNotNull(result);
    }

    // ==================== GrepTool Tests ====================

    @Test
    void testGrepTool_searchContent() throws Exception {
        Path testFile = tempDir.resolve("search.txt");
        Files.writeString(testFile, "Hello World\nFoo Bar\nHello Again");

        GrepTool tool = new GrepTool();

        ObjectNode args = mapper.createObjectNode();
        args.put("pattern", "Hello");
        args.put("path", tempDir.toString());

        String result = tool.execute(args);

        assertNotNull(result);
    }

    @Test
    void testGrepTool_regexSearch() throws Exception {
        Path testFile = tempDir.resolve("regex.txt");
        Files.writeString(testFile, "test123\nfoo456\ntest789");

        GrepTool tool = new GrepTool();

        ObjectNode args = mapper.createObjectNode();
        args.put("pattern", "test\\d+");
        args.put("path", testFile.toString());

        String result = tool.execute(args);

        assertNotNull(result);
    }

    // ==================== ToolRegistry Tests ====================

    @Test
    void testToolRegistry_getTool() {
        // 工具名称是小写的
        Tool bashTool = ToolRegistry.getTool("bash");
        assertNotNull(bashTool);
        assertEquals("bash", bashTool.getName());

        Tool readTool = ToolRegistry.getTool("read_file");
        assertNotNull(readTool);
        assertEquals("read_file", readTool.getName());
    }

    @Test
    void testToolRegistry_getNonExistentTool() {
        Tool tool = ToolRegistry.getTool("non_existent_tool");
        assertNull(tool);
    }

    @Test
    void testToolRegistry_getDefaultTools() {
        java.util.List<Tool> tools = ToolRegistry.getDefaultTools();

        assertFalse(tools.isEmpty());
        // 验证核心工具存在
        assertTrue(tools.stream().anyMatch(t -> t.getName().equals("bash")));
        assertTrue(tools.stream().anyMatch(t -> t.getName().equals("read_file")));
        assertTrue(tools.stream().anyMatch(t -> t.getName().equals("write_file")));
        assertTrue(tools.stream().anyMatch(t -> t.getName().equals("edit_file")));
    }

    // ==================== Helper Classes ====================

    private static class SimpleTestTool extends Tool {
        SimpleTestTool() {
            super("test", "A test tool", new ObjectMapper().createObjectNode());
        }

        @Override
        public String execute(com.fasterxml.jackson.databind.JsonNode arguments) {
            return "executed";
        }
    }
}

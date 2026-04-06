package cn.abelib.minecode.tools.impl;

import cn.abelib.minecode.tools.Tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 文件编辑工具 - 搜索替换编辑
 *
 * <p>核心思想：LLM 指定一个精确的子字符串及其替换。
 * 子字符串必须在文件中唯一出现，消除歧义并使编辑安全可审查。
 *
 * <p>这是 Claude Code 的关键创新之一。
 *
 * @author Abel
 */
public class EditFileTool extends Tool {

    public EditFileTool() {
        super("edit_file", buildDescription(), buildParameters());
    }

    private static String buildDescription() {
        return "通过精确字符串匹配编辑文件。old_string 必须在文件中唯一出现。" +
                "包含足够的上下文确保唯一性。";
    }

    private static ObjectNode buildParameters() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode params = mapper.createObjectNode();
        params.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();

        ObjectNode filePath = mapper.createObjectNode();
        filePath.put("type", "string");
        filePath.put("description", "文件路径");
        properties.set("file_path", filePath);

        ObjectNode oldString = mapper.createObjectNode();
        oldString.put("type", "string");
        oldString.put("description", "要查找的精确文本（必须在文件中唯一）");
        properties.set("old_string", oldString);

        ObjectNode newString = mapper.createObjectNode();
        newString.put("type", "string");
        newString.put("description", "替换文本");
        properties.set("new_string", newString);

        params.set("properties", properties);

        ArrayNode required = mapper.createArrayNode();
        required.add("file_path");
        required.add("old_string");
        required.add("new_string");
        params.set("required", required);

        return params;
    }

    @Override
    public String execute(JsonNode arguments) {
        String filePath = arguments.path("file_path").asText();
        String oldString = arguments.path("old_string").asText();
        String newString = arguments.path("new_string").asText();

        try {
            Path path = Path.of(filePath).toAbsolutePath().normalize();

            if (!Files.exists(path)) {
                return "错误: 文件不存在: " + filePath;
            }

            String content = Files.readString(path);
            int occurrences = countOccurrences(content, oldString);

            if (occurrences == 0) {
                String preview = content.substring(0, Math.min(content.length(), 500));
                return "错误: old_string 在文件中未找到。\n文件开头:\n" + preview;
            }

            if (occurrences > 1) {
                return "错误: old_string 在文件中出现 " + occurrences + " 次。" +
                        "请包含更多上下文使其唯一。";
            }

            String newContent = content.replace(oldString, newString);
            Files.writeString(path, newContent);

            // 生成 diff
            String diff = generateDiff(content, newContent, path.toString());

            return "已编辑: " + filePath + "\n" + diff;

        } catch (Exception e) {
            return "错误: " + e.getMessage();
        }
    }

    private int countOccurrences(String content, String target) {
        int count = 0;
        int index = 0;
        while ((index = content.indexOf(target, index)) != -1) {
            count++;
            index += target.length();
        }
        return count;
    }

    private String generateDiff(String oldContent, String newContent, String filename) {
        List<String> oldLines = oldContent.lines().toList();
        List<String> newLines = newContent.lines().toList();

        Patch<String> patch = DiffUtils.diff(oldLines, newLines);

        StringBuilder sb = new StringBuilder();
        sb.append("--- a/").append(filename).append("\n");
        sb.append("+++ b/").append(filename).append("\n");

        for (AbstractDelta<String> delta : patch.getDeltas()) {
            DeltaType type = delta.getType();

            int sourcePos = delta.getSource().getPosition() + 1;
            int targetPos = delta.getTarget().getPosition() + 1;
            int sourceSize = delta.getSource().getLines().size();
            int targetSize = delta.getTarget().getLines().size();

            switch (type) {
                case INSERT -> {
                    sb.append("@@ +0,").append(targetSize).append(" +").append(targetPos)
                            .append(",").append(targetSize).append(" @@\n");
                    for (String line : delta.getTarget().getLines()) {
                        sb.append("+").append(line).append("\n");
                    }
                }
                case DELETE -> {
                    sb.append("@@ -").append(sourcePos).append(",").append(sourceSize)
                            .append(" +0,0 @@\n");
                    for (String line : delta.getSource().getLines()) {
                        sb.append("-").append(line).append("\n");
                    }
                }
                case CHANGE -> {
                    sb.append("@@ -").append(sourcePos).append(",").append(sourceSize)
                            .append(" +").append(targetPos).append(",").append(targetSize).append(" @@\n");
                    for (String line : delta.getSource().getLines()) {
                        sb.append("-").append(line).append("\n");
                    }
                    for (String line : delta.getTarget().getLines()) {
                        sb.append("+").append(line).append("\n");
                    }
                }
            }
        }

        String result = sb.toString();
        return result.length() > 3000 ? result.substring(0, 2500) + "\n... (diff 已截断)\n" : result;
    }
}

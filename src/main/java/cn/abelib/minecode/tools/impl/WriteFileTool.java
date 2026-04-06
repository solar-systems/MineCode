package cn.abelib.minecode.tools.impl;

import cn.abelib.minecode.tools.Tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文件写入工具
 *
 * @author Abel
 */
public class WriteFileTool extends Tool {

    public WriteFileTool() {
        super("write_file", buildDescription(), buildParameters());
    }

    private static String buildDescription() {
        return "写入文件内容。用于创建新文件或完全重写现有文件。";
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

        ObjectNode content = mapper.createObjectNode();
        content.put("type", "string");
        content.put("description", "文件内容");
        properties.set("content", content);

        params.set("properties", properties);

        ArrayNode required = mapper.createArrayNode();
        required.add("file_path");
        required.add("content");
        params.set("required", required);

        return params;
    }

    @Override
    public String execute(JsonNode arguments) {
        String filePath = arguments.path("file_path").asText();
        String content = arguments.path("content").asText();

        try {
            Path path = Path.of(filePath).toAbsolutePath().normalize();

            // 确保父目录存在
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            Files.writeString(path, content);

            return "文件已写入: " + path + " (" + content.length() + " 字符)";

        } catch (Exception e) {
            return "错误: " + e.getMessage();
        }
    }
}

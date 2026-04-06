package cn.abelib.minecode.tools.impl;

import cn.abelib.minecode.tools.Tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文件读取工具
 *
 * @author Abel
 */
public class ReadFileTool extends Tool {

    public ReadFileTool() {
        super("read_file", buildDescription(), buildParameters());
    }

    private static String buildDescription() {
        return "读取文件内容。支持指定行范围。";
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

        ObjectNode offset = mapper.createObjectNode();
        offset.put("type", "integer");
        offset.put("description", "起始行号（可选）");
        properties.set("offset", offset);

        ObjectNode limit = mapper.createObjectNode();
        limit.put("type", "integer");
        limit.put("description", "读取行数（可选）");
        properties.set("limit", limit);

        params.set("properties", properties);

        ArrayNode required = mapper.createArrayNode();
        required.add("file_path");
        params.set("required", required);

        return params;
    }

    @Override
    public String execute(JsonNode arguments) {
        String filePath = arguments.path("file_path").asText();
        int offset = arguments.path("offset").asInt(0);
        int limit = arguments.path("limit").asInt(0);

        try {
            Path path = Path.of(filePath).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                return "错误: 文件不存在: " + filePath;
            }

            String content = Files.readString(path);

            if (offset > 0 || limit > 0) {
                String[] lines = content.split("\n");
                int start = Math.max(0, offset);
                int end = limit > 0 ? Math.min(lines.length, start + limit) : lines.length;

                StringBuilder sb = new StringBuilder();
                for (int i = start; i < end; i++) {
                    sb.append(String.format("%6d→", i + 1)).append(lines[i]).append("\n");
                }
                return sb.toString();
            }

            // 添加行号
            String[] lines = content.split("\n");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.length; i++) {
                sb.append(String.format("%6d→", i + 1)).append(lines[i]).append("\n");
            }

            return sb.toString();

        } catch (Exception e) {
            return "错误: " + e.getMessage();
        }
    }
}

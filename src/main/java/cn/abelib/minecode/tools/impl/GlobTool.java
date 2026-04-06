package cn.abelib.minecode.tools.impl;

import cn.abelib.minecode.tools.Tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * 文件搜索工具 - Glob 模式匹配
 *
 * @author Abel
 */
public class GlobTool extends Tool {

    public GlobTool() {
        super("glob", buildDescription(), buildParameters());
    }

    private static String buildDescription() {
        return "使用 Glob 模式搜索文件。如 **/*.java 搜索所有 Java 文件。";
    }

    private static ObjectNode buildParameters() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode params = mapper.createObjectNode();
        params.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();

        ObjectNode pattern = mapper.createObjectNode();
        pattern.put("type", "string");
        pattern.put("description", "Glob 模式，如 **/*.java");
        properties.set("pattern", pattern);

        ObjectNode path = mapper.createObjectNode();
        path.put("type", "string");
        path.put("description", "搜索目录（可选，默认当前目录）");
        properties.set("path", path);

        params.set("properties", properties);

        ArrayNode required = mapper.createArrayNode();
        required.add("pattern");
        params.set("required", required);

        return params;
    }

    @Override
    public String execute(JsonNode arguments) {
        String pattern = arguments.path("pattern").asText();
        String basePath = arguments.path("path").asText(".");

        try {
            Path startPath = Paths.get(basePath).toAbsolutePath().normalize();
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);

            StringBuilder sb = new StringBuilder();

            try (Stream<Path> stream = Files.walk(startPath)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> matcher.matches(startPath.relativize(p)))
                        .limit(100)
                        .forEach(p -> sb.append(p).append("\n"));
            }

            String result = sb.toString();
            return result.isEmpty() ? "(未找到匹配文件)" : result;

        } catch (Exception e) {
            return "错误: " + e.getMessage();
        }
    }
}

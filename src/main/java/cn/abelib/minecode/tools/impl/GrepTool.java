package cn.abelib.minecode.tools.impl;

import cn.abelib.minecode.tools.Tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 内容搜索工具 - 正则表达式搜索
 *
 * @author Abel
 */
public class GrepTool extends Tool {

    public GrepTool() {
        super("grep", buildDescription(), buildParameters());
    }

    private static String buildDescription() {
        return "使用正则表达式搜索文件内容。返回匹配的文件和行。";
    }

    private static ObjectNode buildParameters() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode params = mapper.createObjectNode();
        params.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();

        ObjectNode pattern = mapper.createObjectNode();
        pattern.put("type", "string");
        pattern.put("description", "正则表达式模式");
        properties.set("pattern", pattern);

        ObjectNode path = mapper.createObjectNode();
        path.put("type", "string");
        path.put("description", "搜索目录或文件");
        properties.set("path", path);

        ObjectNode glob = mapper.createObjectNode();
        glob.put("type", "string");
        glob.put("description", "文件过滤模式（如 *.java）");
        properties.set("glob", glob);

        params.set("properties", properties);

        ArrayNode required = mapper.createArrayNode();
        required.add("pattern");
        params.set("required", required);

        return params;
    }

    @Override
    public String execute(JsonNode arguments) {
        String patternStr = arguments.path("pattern").asText();
        String basePath = arguments.path("path").asText(".");
        String globPattern = arguments.path("glob").asText("*");

        try {
            Pattern pattern = Pattern.compile(patternStr);
            Path startPath = Paths.get(basePath).toAbsolutePath().normalize();

            StringBuilder sb = new StringBuilder();
            int matchCount = 0;
            int maxMatches = 50;

            try (Stream<Path> stream = Files.walk(startPath)) {
                for (Path path : stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().matches(globToRegex(globPattern)))
                        .toList()) {

                    if (matchCount >= maxMatches) break;

                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(path)))) {
                        String line;
                        int lineNum = 0;
                        while ((line = reader.readLine()) != null && matchCount < maxMatches) {
                            lineNum++;
                            if (pattern.matcher(line).find()) {
                                sb.append(path).append(":").append(lineNum).append(": ").append(line).append("\n");
                                matchCount++;
                            }
                        }
                    } catch (Exception ignored) {
                        // 跳过无法读取的文件
                    }
                }
            }

            String result = sb.toString();
            return result.isEmpty() ? "(未找到匹配)" : result + "\n找到 " + matchCount + " 处匹配";

        } catch (Exception e) {
            return "错误: " + e.getMessage();
        }
    }

    private String globToRegex(String glob) {
        return glob.replace(".", "\\.").replace("*", ".*");
    }
}

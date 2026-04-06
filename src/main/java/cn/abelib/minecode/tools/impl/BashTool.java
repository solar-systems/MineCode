package cn.abelib.minecode.tools.impl;

import cn.abelib.minecode.tools.Tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

/**
 * Bash 工具 - Shell 命令执行
 *
 * <p>功能：
 * <ul>
 *   <li>输出捕获与截断（保留头尾）</li>
 *   <li>超时支持</li>
 *   <li>危险命令检测</li>
 *   <li>工作目录跟踪（cd 感知）</li>
 * </ul>
 *
 * <p>注意：工作目录使用 ThreadLocal 存储，确保多线程/多会话安全。
 *
 * @author Abel
 */
public class BashTool extends Tool {
    private static final Logger log = LoggerFactory.getLogger(BashTool.class);

    // 跟踪当前工作目录（使用 ThreadLocal 确保线程安全）
    private static final ThreadLocal<String> currentWorkingDir = ThreadLocal.withInitial(() -> null);

    // 危险命令模式
    private static final Pattern[] DANGEROUS_PATTERNS = {
            Pattern.compile("\\brm\\s+(-\\w*)?-r\\w*\\s+(/|~|\\$HOME)"),
            Pattern.compile("\\brm\\s+(-\\w*)?-rf\\b"),  // 修复: 匹配 -rf 后面可以没有空格
            Pattern.compile("\\bmkfs\\b"),
            Pattern.compile("\\bdd\\s+.*of=/dev/"),
            Pattern.compile(">\\s*/dev/sd[a-z]"),
            Pattern.compile("\\bchmod\\s+(-R\\s+)?777\\s+/"),
            Pattern.compile(":\\(\\)\\s*\\{.*:\\|:.*\\}"),
            Pattern.compile("\\b(curl|wget)\\b.*\\|\\s*(sudo\\s+)?bash")
    };

    public BashTool() {
        super("bash", buildDescription(), buildParameters());
    }

    private static String buildDescription() {
        return "执行 Shell 命令。返回 stdout、stderr 和退出码。用于运行测试、安装包、git 操作等。";
    }

    private static ObjectNode buildParameters() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode params = mapper.createObjectNode();
        params.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();

        ObjectNode command = mapper.createObjectNode();
        command.put("type", "string");
        command.put("description", "要执行的 Shell 命令");
        properties.set("command", command);

        ObjectNode timeout = mapper.createObjectNode();
        timeout.put("type", "integer");
        timeout.put("description", "超时秒数（默认 120）");
        properties.set("timeout", timeout);

        params.set("properties", properties);

        ArrayNode required = mapper.createArrayNode();
        required.add("command");
        params.set("required", required);

        return params;
    }

    @Override
    public String execute(JsonNode arguments) {
        String command = arguments.path("command").asText();
        int timeout = arguments.path("timeout").asInt(120);

        String warning = checkDangerous(command);
        if (warning != null) {
            return "⚠ 已阻止: " + warning + "\n命令: " + command;
        }

        String cwd = currentWorkingDir.get();
        if (cwd == null) {
            cwd = System.getProperty("user.dir");
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
            pb.directory(new File(cwd));
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(timeout, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "错误: 命令在 " + timeout + " 秒后超时";
            }

            if (process.exitValue() == 0) {
                updateWorkingDir(command, cwd);
            }

            String result = output.toString();
            if (process.exitValue() != 0) {
                result += "\n[退出码: " + process.exitValue() + "]";
            }

            if (result.length() > 15000) {
                result = result.substring(0, 6000) +
                        "\n\n... 截断 ...\n\n" +
                        result.substring(result.length() - 3000);
            }

            return result.isEmpty() ? "(无输出)" : result.trim();

        } catch (Exception e) {
            return "错误: " + e.getMessage();
        }
    }

    private String checkDangerous(String command) {
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(command).find()) {
                return "检测到危险操作";
            }
        }
        return null;
    }

    private void updateWorkingDir(String command, String currentDir) {
        String[] parts = command.split("&&");
        for (String part : parts) {
            part = part.trim();
            if (part.startsWith("cd ")) {
                String target = part.substring(3).trim().replace("\"", "").replace("'", "");
                if (!target.isEmpty()) {
                    File newDir = new File(currentDir, target);
                    if (newDir.isDirectory()) {
                        currentWorkingDir.set(newDir.getAbsolutePath());
                    }
                }
            }
        }
    }

    /**
     * 重置当前线程的工作目录
     */
    public static void resetWorkingDir() {
        currentWorkingDir.remove();
    }

    /**
     * 设置当前线程的工作目录
     *
     * @param dir 工作目录路径
     */
    public static void setWorkingDir(String dir) {
        currentWorkingDir.set(dir);
    }

    /**
     * 获取当前线程的工作目录
     *
     * @return 工作目录路径，如果未设置则返回 null
     */
    public static String getWorkingDir() {
        return currentWorkingDir.get();
    }
}

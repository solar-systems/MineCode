package cn.abelib.minecode.tools.builtin;

import cn.abelib.minecode.tools.annotation.Tool;
import cn.abelib.minecode.tools.annotation.ToolParam;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.script.ScriptEngineManager;
import javax.script.ScriptEngine;

/**
 * 示例工具类 - 使用 @Tool 注解定义工具
 *
 * <p>使用示例：
 * <pre>{@code
 * // 扫描并注册
 * ToolScanner scanner = new ToolScanner();
 * scanner.scanAndRegister(new AnnotatedTools());
 *
 * // 或者添加到 Agent
 * Agent agent = Agent.builder()
 *     .llm(llm)
 *     .tools(scanner.scan(new AnnotatedTools()))
 *     .build();
 * }</pre>
 *
 * @author Abel
 */
public class AnnotatedTools {

    private static final ScriptEngine scriptEngine = new ScriptEngineManager().getEngineByName("js");

    /**
     * 获取当前时间
     */
    @Tool(description = "获取当前日期和时间")
    public String getCurrentTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 计算数学表达式
     */
    @Tool(name = "calculate", description = "计算数学表达式的值")
    public String calculate(
            @ToolParam(name = "expression", description = "数学表达式，如 '1+2*3'") String expression) {

        try {
            Object result = scriptEngine.eval(expression);
            return String.valueOf(result);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * 生成随机数
     */
    @Tool(description = "生成指定范围内的随机数")
    public String random(
            @ToolParam(name = "min", description = "最小值", required = true) int min,
            @ToolParam(name = "max", description = "最大值", required = true) int max) {

        if (min > max) {
            return "Error: min must be less than or equal to max";
        }

        int result = (int) (Math.random() * (max - min + 1)) + min;
        return String.valueOf(result);
    }

    /**
     * 字符串操作
     */
    @Tool(name = "string_op", description = "执行字符串操作")
    public String stringOperation(
            @ToolParam(name = "operation", description = "操作类型",
                    enumValues = {"upper", "lower", "reverse", "length"}) String operation,
            @ToolParam(name = "text", description = "输入文本") String text) {

        if (text == null || text.isEmpty()) {
            return "Error: text is required";
        }

        return switch (operation.toLowerCase()) {
            case "upper" -> text.toUpperCase();
            case "lower" -> text.toLowerCase();
            case "reverse" -> new StringBuilder(text).reverse().toString();
            case "length" -> String.valueOf(text.length());
            default -> "Error: unknown operation: " + operation;
        };
    }
}

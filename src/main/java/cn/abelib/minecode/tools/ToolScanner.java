package cn.abelib.minecode.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 工具扫描器 - 扫描对象中的 @Tool 注解方法并注册为工具
 *
 * <p>使用示例：
 * <pre>{@code
 * // 定义工具类
 * public class MyTools {
 *
 *     @Tool(name = "get_weather", description = "查询天气")
 *     public String getWeather(
 *         @ToolParam(name = "city", description = "城市") String city) {
 *         return "Weather: sunny";
 *     }
 *
 *     @Tool(description = "获取当前时间")
 *     public String getCurrentTime() {
 *         return java.time.LocalDateTime.now().toString();
 *     }
 * }
 *
 * // 扫描并注册
 * ToolScanner scanner = new ToolScanner();
 * List<Tool> tools = scanner.scan(new MyTools());
 *
 * // 注册到 ToolRegistry
 * tools.forEach(ToolRegistry::register);
 *
 * // 或者直接注册
 * scanner.scanAndRegister(new MyTools());
 * }</pre>
 *
 * @author Abel
 * @see cn.abelib.minecode.tools.annotation.Tool
 * @see cn.abelib.minecode.tools.annotation.ToolParam
 */
public class ToolScanner {

    private static final Logger log = LoggerFactory.getLogger(ToolScanner.class);
    private static final Class<cn.abelib.minecode.tools.annotation.Tool> TOOL_ANNOTATION =
            cn.abelib.minecode.tools.annotation.Tool.class;

    /**
     * 扫描对象中的所有 @Tool 方法
     *
     * @param target 目标对象
     * @return 扫描到的工具列表
     */
    public List<Tool> scan(Object target) {
        return scan(target, null);
    }

    /**
     * 扫描对象中的所有 @Tool 方法（带名称前缀）
     *
     * @param target 目标对象
     * @param prefix 工具名称前缀（可为 null）
     * @return 扫描到的工具列表
     */
    public List<Tool> scan(Object target, String prefix) {
        List<Tool> tools = new ArrayList<>();
        Class<?> clazz = target.getClass();

        // 扫描所有方法（包括父类）
        for (Method method : clazz.getMethods()) {
            cn.abelib.minecode.tools.annotation.Tool annotation = method.getAnnotation(TOOL_ANNOTATION);
            if (annotation != null) {
                try {
                    AnnotatedTool tool = new AnnotatedTool(target, method);

                    // 如果有前缀，创建包装工具
                    if (prefix != null && !prefix.isEmpty()) {
                        // 可以创建带前缀的包装器，这里暂不实现
                        tools.add(tool);
                    } else {
                        tools.add(tool);
                    }

                    log.debug("Scanned tool: {} from {}.{}",
                            tool.getName(), clazz.getSimpleName(), method.getName());

                } catch (Exception e) {
                    log.error("Failed to create tool from method: {}.{}",
                            clazz.getSimpleName(), method.getName(), e);
                }
            }
        }

        log.info("Scanned {} tools from {}", tools.size(), clazz.getSimpleName());
        return tools;
    }

    /**
     * 扫描并注册工具到 ToolRegistry
     *
     * @param target 目标对象
     * @return 扫描到的工具列表
     */
    public List<Tool> scanAndRegister(Object target) {
        List<Tool> tools = scan(target);
        tools.forEach(ToolRegistry::register);
        return tools;
    }

    /**
     * 扫描多个对象
     *
     * @param targets 目标对象数组
     * @return 所有扫描到的工具列表
     */
    public List<Tool> scanAll(Object... targets) {
        List<Tool> allTools = new ArrayList<>();
        for (Object target : targets) {
            allTools.addAll(scan(target));
        }
        return allTools;
    }

    /**
     * 扫描多个对象并注册
     *
     * @param targets 目标对象数组
     * @return 所有扫描到的工具列表
     */
    public List<Tool> scanAllAndRegister(Object... targets) {
        List<Tool> allTools = scanAll(targets);
        allTools.forEach(ToolRegistry::register);
        return allTools;
    }

    /**
     * 验证工具方法是否有效
     *
     * @param method 方法
     * @return 是否为有效的工具方法
     */
    public static boolean isValidToolMethod(Method method) {
        cn.abelib.minecode.tools.annotation.Tool annotation = method.getAnnotation(TOOL_ANNOTATION);
        if (annotation == null) {
            return false;
        }

        // 检查描述是否为空
        if (annotation.description().isEmpty()) {
            log.warn("Tool method {} has empty description", method.getName());
            return false;
        }

        // 检查返回类型
        Class<?> returnType = method.getReturnType();
        if (returnType == void.class) {
            log.warn("Tool method {} returns void, should return String or other type", method.getName());
            return false;
        }

        return true;
    }

    /**
     * 获取工具方法列表（不创建 Tool 实例）
     *
     * @param targetClass 目标类
     * @return 工具方法列表
     */
    public static List<Method> getToolMethods(Class<?> targetClass) {
        List<Method> toolMethods = new ArrayList<>();

        for (Method method : targetClass.getMethods()) {
            if (method.getAnnotation(TOOL_ANNOTATION) != null) {
                toolMethods.add(method);
            }
        }

        return toolMethods;
    }
}

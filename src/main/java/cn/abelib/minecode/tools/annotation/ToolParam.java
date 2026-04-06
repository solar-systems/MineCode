package cn.abelib.minecode.tools.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具参数注解 - 标注工具方法的参数
 *
 * <p>使用示例：
 * <pre>{@code
 * @Tool(name = "search", description = "搜索文件")
 * public String search(
 *     @ToolParam(name = "pattern", description = "搜索模式", required = true) String pattern,
 *     @ToolParam(name = "path", description = "搜索路径", required = false) String path,
 *     @ToolParam(name = "max_results", description = "最大结果数", required = false) int maxResults) {
 *     // 实现...
 * }
 * }</pre>
 *
 * @author Abel
 * @see Tool
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolParam {

    /**
     * 参数名称
     */
    String name();

    /**
     * 参数描述
     */
    String description() default "";

    /**
     * 是否必需
     */
    boolean required() default true;

    /**
     * 默认值（字符串表示）
     */
    String defaultValue() default "";

    /**
     * 参数类型提示
     *
     * <p>用于生成 JSON Schema，如果不指定则自动推断
     */
    ParamType type() default ParamType.AUTO;

    /**
     * 枚举值（用于限制参数取值范围）
     */
    String[] enumValues() default {};

    /**
     * 参数类型枚举
     */
    enum ParamType {
        AUTO,       // 自动推断
        STRING,     // 字符串
        INTEGER,    // 整数
        NUMBER,     // 浮点数
        BOOLEAN,    // 布尔值
        ARRAY,      // 数组
        OBJECT      // 对象
    }
}

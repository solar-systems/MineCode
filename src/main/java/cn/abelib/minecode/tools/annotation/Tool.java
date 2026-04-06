package cn.abelib.minecode.tools.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具注解 - 标注方法为 LLM 可调用的工具
 *
 * <p>使用示例：
 * <pre>{@code
 * public class MyTools {
 *
 *     @Tool(name = "get_weather", description = "查询指定城市的天气")
 *     public String getWeather(
 *         @ToolParam(name = "city", description = "城市名称", required = true) String city,
 *         @ToolParam(name = "unit", description = "温度单位", required = false) String unit) {
 *         return "Weather in " + city + ": sunny, 25" + (unit != null ? unit : "C");
 *     }
 *
 *     @Tool(description = "获取当前时间")  // name 默认为方法名
 *     public String getCurrentTime() {
 *         return java.time.LocalDateTime.now().toString();
 *     }
 * }
 * }</pre>
 *
 * @author Abel
 * @see ToolParam
 * @see cn.abelib.minecode.tools.ToolScanner
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Tool {

    /**
     * 工具名称（LLM 调用时使用）
     *
     * <p>如果不指定，默认使用方法名
     */
    String name() default "";

    /**
     * 工具描述（告诉 LLM 这个工具做什么）
     */
    String description();

    /**
     * 是否为危险操作
     *
     * <p>危险操作会在执行前需要额外确认
     */
    boolean dangerous() default false;
}

package cn.abelib.minecode.tools;

import cn.abelib.minecode.tools.annotation.ToolParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

/**
 * 注解工具 - 将 @Tool 注解方法包装为 Tool 实例
 *
 * <p>此类由 ToolScanner 内部使用，通常不需要直接实例化。
 *
 * @author Abel
 * @see cn.abelib.minecode.tools.annotation.Tool
 * @see ToolScanner
 */
public class AnnotatedTool extends Tool {

    private static final Logger log = LoggerFactory.getLogger(AnnotatedTool.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final Object target;
    private final Method method;
    private final List<ParamInfo> paramInfos;
    private final boolean dangerous;

    /**
     * 参数信息
     */
    private static class ParamInfo {
        final String name;
        final String description;
        final boolean required;
        final Class<?> type;
        final ToolParam.ParamType schemaType;
        final String[] enumValues;
        final String defaultValue;

        ParamInfo(String name, String description, boolean required, Class<?> type,
                  ToolParam.ParamType schemaType, String[] enumValues, String defaultValue) {
            this.name = name;
            this.description = description;
            this.required = required;
            this.type = type;
            this.schemaType = schemaType;
            this.enumValues = enumValues;
            this.defaultValue = defaultValue;
        }
    }

    /**
     * 构造函数
     *
     * @param target 目标对象（包含 @Tool 方法的实例）
     * @param method 被 @Tool 标注的方法
     */
    public AnnotatedTool(Object target, Method method) {
        super(
                extractToolName(method),
                extractDescription(method),
                buildParametersSchema(method)
        );
        this.target = target;
        this.method = method;
        this.dangerous = method.getAnnotation(cn.abelib.minecode.tools.annotation.Tool.class).dangerous();
        this.paramInfos = extractParamInfos(method);

        log.debug("Created AnnotatedTool: {} -> {}.{}()",
                name, target.getClass().getSimpleName(), method.getName());
    }

    @Override
    public String execute(JsonNode arguments) {
        try {
            // 构建方法参数
            Object[] args = buildMethodArguments(arguments);

            // 调用方法
            Object result = method.invoke(target, args);

            // 返回结果
            if (result == null) {
                return "";
            }
            return result.toString();

        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("Tool execution failed: {}", name, cause);
            return "Error: " + cause.getMessage();
        }
    }

    /**
     * 构建方法参数
     */
    private Object[] buildMethodArguments(JsonNode arguments) {
        Object[] args = new Object[paramInfos.size()];

        for (int i = 0; i < paramInfos.size(); i++) {
            ParamInfo param = paramInfos.get(i);
            JsonNode valueNode = arguments.path(param.name);

            args[i] = convertArgument(valueNode, param);
        }

        return args;
    }

    /**
     * 转换参数值
     */
    private Object convertArgument(JsonNode valueNode, ParamInfo param) {
        // 如果没有值，使用默认值
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            if (!param.defaultValue.isEmpty()) {
                return convertType(param.defaultValue, param.type);
            }
            if (!param.required) {
                return getDefaultValue(param.type);
            }
            return null;
        }

        return convertJsonNode(valueNode, param.type);
    }

    /**
     * 将 JsonNode 转换为指定类型
     */
    private Object convertJsonNode(JsonNode node, Class<?> type) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return getDefaultValue(type);
        }

        if (type == String.class) {
            return node.asText();
        } else if (type == int.class || type == Integer.class) {
            return node.asInt();
        } else if (type == long.class || type == Long.class) {
            return node.asLong();
        } else if (type == double.class || type == Double.class) {
            return node.asDouble();
        } else if (type == float.class || type == Float.class) {
            return (float) node.asDouble();
        } else if (type == boolean.class || type == Boolean.class) {
            return node.asBoolean();
        } else if (type == JsonNode.class) {
            return node;
        } else {
            // 尝试转换为字符串
            return node.asText();
        }
    }

    /**
     * 将字符串转换为指定类型
     */
    private Object convertType(String value, Class<?> type) {
        if (value == null || value.isEmpty()) {
            return getDefaultValue(type);
        }

        try {
            if (type == String.class) {
                return value;
            } else if (type == int.class || type == Integer.class) {
                return Integer.parseInt(value);
            } else if (type == long.class || type == Long.class) {
                return Long.parseLong(value);
            } else if (type == double.class || type == Double.class) {
                return Double.parseDouble(value);
            } else if (type == float.class || type == Float.class) {
                return Float.parseFloat(value);
            } else if (type == boolean.class || type == Boolean.class) {
                return Boolean.parseBoolean(value);
            }
        } catch (NumberFormatException e) {
            log.warn("Failed to convert '{}' to {}", value, type.getSimpleName());
        }

        return value;
    }

    /**
     * 获取类型的默认值
     */
    private Object getDefaultValue(Class<?> type) {
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        if (type == boolean.class) return false;
        return null;
    }

    /**
     * 提取工具名称
     */
    private static String extractToolName(Method method) {
        cn.abelib.minecode.tools.annotation.Tool annotation =
                method.getAnnotation(cn.abelib.minecode.tools.annotation.Tool.class);
        String toolName = annotation.name();
        return toolName.isEmpty() ? method.getName() : toolName;
    }

    /**
     * 提取工具描述
     */
    private static String extractDescription(Method method) {
        cn.abelib.minecode.tools.annotation.Tool annotation =
                method.getAnnotation(cn.abelib.minecode.tools.annotation.Tool.class);
        return annotation.description();
    }

    /**
     * 构建参数 Schema
     */
    private static ObjectNode buildParametersSchema(Method method) {
        ObjectNode params = mapper.createObjectNode();
        params.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();
        ArrayNode required = mapper.createArrayNode();

        Parameter[] parameters = method.getParameters();
        for (Parameter parameter : parameters) {
            ToolParam paramAnnotation = parameter.getAnnotation(ToolParam.class);
            if (paramAnnotation == null) {
                // 没有 @ToolParam 注解，跳过或使用默认值
                continue;
            }

            String paramName = paramAnnotation.name();
            ObjectNode paramSchema = buildParamSchema(parameter.getType(), paramAnnotation);
            properties.set(paramName, paramSchema);

            if (paramAnnotation.required()) {
                required.add(paramName);
            }
        }

        params.set("properties", properties);
        if (required.size() > 0) {
            params.set("required", required);
        }

        return params;
    }

    /**
     * 构建单个参数的 Schema
     */
    private static ObjectNode buildParamSchema(Class<?> type, ToolParam annotation) {
        ObjectNode schema = mapper.createObjectNode();

        // 确定类型
        String jsonType = determineJsonType(type, annotation.type());
        schema.put("type", jsonType);

        // 描述
        if (!annotation.description().isEmpty()) {
            schema.put("description", annotation.description());
        }

        // 枚举值
        if (annotation.enumValues().length > 0) {
            ArrayNode enumArray = schema.putArray("enum");
            for (String value : annotation.enumValues()) {
                enumArray.add(value);
            }
        }

        // 默认值
        if (!annotation.defaultValue().isEmpty()) {
            if (jsonType.equals("string")) {
                schema.put("default", annotation.defaultValue());
            } else if (jsonType.equals("integer")) {
                schema.put("default", Integer.parseInt(annotation.defaultValue()));
            } else if (jsonType.equals("number")) {
                schema.put("default", Double.parseDouble(annotation.defaultValue()));
            } else if (jsonType.equals("boolean")) {
                schema.put("default", Boolean.parseBoolean(annotation.defaultValue()));
            }
        }

        return schema;
    }

    /**
     * 确定 JSON Schema 类型
     */
    private static String determineJsonType(Class<?> javaType, ToolParam.ParamType schemaType) {
        // 如果显式指定了类型，使用指定的
        if (schemaType != ToolParam.ParamType.AUTO) {
            return schemaType.name().toLowerCase();
        }

        // 自动推断
        if (javaType == String.class) return "string";
        if (javaType == int.class || javaType == Integer.class ||
                javaType == long.class || javaType == Long.class) return "integer";
        if (javaType == double.class || javaType == Double.class ||
                javaType == float.class || javaType == Float.class) return "number";
        if (javaType == boolean.class || javaType == Boolean.class) return "boolean";
        if (javaType.isArray() || javaType == List.class) return "array";

        return "string"; // 默认为字符串
    }

    /**
     * 提取参数信息列表
     */
    private static List<ParamInfo> extractParamInfos(Method method) {
        List<ParamInfo> infos = new ArrayList<>();
        Parameter[] parameters = method.getParameters();

        for (Parameter parameter : parameters) {
            ToolParam annotation = parameter.getAnnotation(ToolParam.class);
            if (annotation == null) {
                // 没有注解，创建默认参数信息
                infos.add(new ParamInfo(
                        parameter.getName(),
                        "",
                        false,
                        parameter.getType(),
                        ToolParam.ParamType.AUTO,
                        new String[0],
                        ""
                ));
            } else {
                infos.add(new ParamInfo(
                        annotation.name(),
                        annotation.description(),
                        annotation.required(),
                        parameter.getType(),
                        annotation.type(),
                        annotation.enumValues(),
                        annotation.defaultValue()
                ));
            }
        }

        return infos;
    }

    /**
     * 是否为危险操作
     */
    public boolean isDangerous() {
        return dangerous;
    }

    /**
     * 获取目标对象
     */
    public Object getTarget() {
        return target;
    }

    /**
     * 获取方法
     */
    public Method getMethod() {
        return method;
    }
}

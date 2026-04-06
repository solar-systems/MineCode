package cn.abelib.minecode.tools;

import cn.abelib.minecode.agent.Agent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 工具基类 - 所有工具的抽象接口
 *
 * <p>设计灵感来自 Claude Code 的工具系统。每个工具需要提供：
 * <ul>
 *   <li>name - 工具名称，LLM 调用时使用</li>
 *   <li>description - 工具描述，告诉 LLM 这个工具做什么</li>
 *   <li>parameters - JSON Schema 格式的参数定义</li>
 * </ul>
 *
 * @author Abel
 * @see Agent
 */
public abstract class Tool {

    /**
     * 工具名称
     */
    protected final String name;

    /**
     * 工具描述
     */
    protected final String description;

    /**
     * 参数 Schema (JSON Schema 格式)
     */
    protected final ObjectNode parameters;

    protected Tool(String name, String description, ObjectNode parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }

    /**
     * 获取工具名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取工具描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 执行工具
     *
     * @param arguments 工具参数
     * @return 执行结果字符串
     */
    public abstract String execute(JsonNode arguments);

    /**
     * 生成 OpenAI Function Calling 格式的 Schema
     */
    public ObjectNode toSchema() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "function");

        ObjectNode function = mapper.createObjectNode();
        function.put("name", name);
        function.put("description", description);
        function.set("parameters", parameters);

        schema.set("function", function);
        return schema;
    }
}

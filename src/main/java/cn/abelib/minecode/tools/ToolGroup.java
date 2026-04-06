package cn.abelib.minecode.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 工具组定义 - 将相关工具组织在一起
 *
 * <p>用于按功能分组管理工具，支持整体激活/禁用。
 *
 * @author Abel
 */
public class ToolGroup {

    private final String name;
    private final String description;
    private final List<Tool> tools;
    private final boolean defaultActive;
    private boolean active;

    /**
     * 创建工具组
     *
     * @param name        组名
     * @param description 描述
     * @param tools       包含的工具
     */
    public ToolGroup(String name, String description, Tool... tools) {
        this(name, description, false, tools);
    }

    /**
     * 创建工具组（指定默认状态）
     *
     * @param name         组名
     * @param description  描述
     * @param defaultActive 默认是否激活
     * @param tools        包含的工具
     */
    public ToolGroup(String name, String description, boolean defaultActive, Tool... tools) {
        this.name = name;
        this.description = description;
        // 过滤掉 null 工具
        this.tools = new ArrayList<>();
        for (Tool tool : tools) {
            if (tool != null) {
                this.tools.add(tool);
            }
        }
        this.defaultActive = defaultActive;
        this.active = defaultActive;
    }

    /**
     * 获取组名
     */
    public String getName() {
        return name;
    }

    /**
     * 获取描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 获取工具列表
     */
    public List<Tool> getTools() {
        return Collections.unmodifiableList(tools);
    }

    /**
     * 添加工具
     */
    public void addTool(Tool tool) {
        if (tool != null && !tools.contains(tool)) {
            tools.add(tool);
        }
    }

    /**
     * 移除工具
     */
    public void removeTool(Tool tool) {
        tools.remove(tool);
    }

    /**
     * 根据名称查找工具
     */
    public Tool getTool(String toolName) {
        return tools.stream()
                .filter(t -> t.getName().equals(toolName))
                .findFirst()
                .orElse(null);
    }

    /**
     * 是否包含指定工具
     */
    public boolean containsTool(String toolName) {
        return tools.stream()
                .filter(Objects::nonNull)
                .anyMatch(t -> t.getName().equals(toolName));
    }

    /**
     * 是否激活
     */
    public boolean isActive() {
        return active;
    }

    /**
     * 激活工具组
     */
    public void activate() {
        this.active = true;
    }

    /**
     * 禁用工具组
     */
    public void deactivate() {
        this.active = false;
    }

    /**
     * 切换激活状态
     */
    public void toggle() {
        this.active = !this.active;
    }

    /**
     * 重置为默认状态
     */
    public void reset() {
        this.active = defaultActive;
    }

    /**
     * 获取工具数量
     */
    public int size() {
        return tools.size();
    }

    @Override
    public String toString() {
        return String.format("ToolGroup[%s: %d tools, active=%s]", name, tools.size(), active);
    }
}

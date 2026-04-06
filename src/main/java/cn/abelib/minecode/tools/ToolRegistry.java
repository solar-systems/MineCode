package cn.abelib.minecode.tools;

import cn.abelib.minecode.tools.impl.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 工具注册器
 *
 * @author Abel
 */
public class ToolRegistry {

    private static final List<Tool> DEFAULT_TOOLS = new CopyOnWriteArrayList<>();

    static {
        DEFAULT_TOOLS.add(new BashTool());
        DEFAULT_TOOLS.add(new ReadFileTool());
        DEFAULT_TOOLS.add(new WriteFileTool());
        DEFAULT_TOOLS.add(new EditFileTool());
        DEFAULT_TOOLS.add(new GlobTool());
        DEFAULT_TOOLS.add(new GrepTool());
        DEFAULT_TOOLS.add(new AgentTool());
    }

    /**
     * 获取默认工具列表
     */
    public static List<Tool> getDefaultTools() {
        return new ArrayList<>(DEFAULT_TOOLS);
    }

    /**
     * 根据名称查找工具
     */
    public static Tool getTool(String name) {
        for (Tool tool : DEFAULT_TOOLS) {
            if (tool.getName().equals(name)) {
                return tool;
            }
        }
        return null;
    }

    /**
     * 注册新工具
     */
    public static void register(Tool tool) {
        DEFAULT_TOOLS.add(tool);
    }
}

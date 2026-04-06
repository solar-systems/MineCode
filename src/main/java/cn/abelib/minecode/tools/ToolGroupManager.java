package cn.abelib.minecode.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 工具组管理器 - 管理工具分组与动态激活
 *
 * <p>核心功能：
 * <ul>
 *   <li>将工具按功能分组管理</li>
 *   <li>运行时动态激活/禁用工具组</li>
 *   <li>支持预设模式快速切换</li>
 *   <li>避免 LLM 面对过多工具时混淆</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * ToolGroupManager manager = new ToolGroupManager();
 *
 * // 注册工具组
 * manager.registerGroup(new ToolGroup("file", "文件操作",
 *     new ReadFileTool(), new WriteFileTool()));
 *
 * // 激活/禁用
 * manager.activateGroup("file");
 * manager.deactivateGroup("execute");
 *
 * // 获取激活的工具
 * List<Tool> tools = manager.getActiveTools();
 *
 * // 使用预设模式
 * manager.applyPreset(ToolPreset.STANDARD);
 * }</pre>
 *
 * @author Abel
 */
public class ToolGroupManager {

    private static final Logger log = LoggerFactory.getLogger(ToolGroupManager.class);

    private final Map<String, ToolGroup> groups = new LinkedHashMap<>();
    private final Map<String, Tool> allTools = new HashMap<>();

    public ToolGroupManager() {
        // 注册默认工具组
        registerDefaultGroups();
    }

    /**
     * 注册默认工具组
     */
    private void registerDefaultGroups() {
        // 文件操作组（默认激活）
        registerGroup(new ToolGroup("file", "文件操作工具", true,
                ToolRegistry.getTool("read_file"),
                ToolRegistry.getTool("write_file"),
                ToolRegistry.getTool("edit_file")
        ));

        // 搜索工具组（默认激活）
        registerGroup(new ToolGroup("search", "搜索工具", true,
                ToolRegistry.getTool("glob"),
                ToolRegistry.getTool("grep")
        ));

        // 命令执行组（默认禁用，危险）
        registerGroup(new ToolGroup("execute", "命令执行（危险操作）", false,
                ToolRegistry.getTool("bash")
        ));

        // 子代理组（默认激活）
        registerGroup(new ToolGroup("agent", "子代理工具", true,
                ToolRegistry.getTool("agent")
        ));

        log.debug("Registered {} default tool groups", groups.size());
    }

    /**
     * 注册工具组
     *
     * @param group 工具组
     */
    public void registerGroup(ToolGroup group) {
        if (group == null) {
            return;
        }

        groups.put(group.getName(), group);

        // 注册所有工具
        for (Tool tool : group.getTools()) {
            if (tool != null) {
                allTools.put(tool.getName(), tool);
            }
        }

        log.debug("Registered tool group: {} with {} tools", group.getName(), group.size());
    }

    /**
     * 注销工具组
     *
     * @param groupName 组名
     */
    public void unregisterGroup(String groupName) {
        ToolGroup removed = groups.remove(groupName);
        if (removed != null) {
            log.debug("Unregistered tool group: {}", groupName);
        }
    }

    /**
     * 激活工具组
     *
     * @param groupName 组名
     */
    public void activateGroup(String groupName) {
        ToolGroup group = groups.get(groupName);
        if (group != null) {
            group.activate();
            log.info("Activated tool group: {}", groupName);
        } else {
            log.warn("Tool group not found: {}", groupName);
        }
    }

    /**
     * 禁用工具组
     *
     * @param groupName 组名
     */
    public void deactivateGroup(String groupName) {
        ToolGroup group = groups.get(groupName);
        if (group != null) {
            group.deactivate();
            log.info("Deactivated tool group: {}", groupName);
        } else {
            log.warn("Tool group not found: {}", groupName);
        }
    }

    /**
     * 切换工具组状态
     *
     * @param groupName 组名
     */
    public void toggleGroup(String groupName) {
        ToolGroup group = groups.get(groupName);
        if (group != null) {
            group.toggle();
            log.info("Toggled tool group: {} -> {}", groupName, group.isActive());
        }
    }

    /**
     * 检查工具组是否激活
     *
     * @param groupName 组名
     * @return 是否激活
     */
    public boolean isGroupActive(String groupName) {
        ToolGroup group = groups.get(groupName);
        return group != null && group.isActive();
    }

    /**
     * 获取所有激活的工具
     *
     * @return 激活的工具列表
     */
    public List<Tool> getActiveTools() {
        return groups.values().stream()
                .filter(ToolGroup::isActive)
                .flatMap(group -> group.getTools().stream())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 获取激活工具的 Schema 列表
     *
     * @return Schema 列表
     */
    public List<ObjectNode> getActiveToolSchemas() {
        return getActiveTools().stream()
                .map(Tool::toSchema)
                .collect(Collectors.toList());
    }

    /**
     * 应用预设模式
     *
     * @param preset 预设模式
     */
    public void applyPreset(ToolPreset preset) {
        if (preset == null) {
            return;
        }

        // 先禁用所有组
        groups.values().forEach(ToolGroup::deactivate);

        // 激活预设中指定的工具
        Set<String> toolNames = new HashSet<>(Arrays.asList(preset.getToolNames()));

        for (ToolGroup group : groups.values()) {
            // 如果组中有任何工具在预设中，激活该组
            boolean hasTool = group.getTools().stream()
                    .anyMatch(t -> t != null && toolNames.contains(t.getName()));

            if (hasTool) {
                group.activate();
            }
        }

        log.info("Applied preset: {} -> {} active tools", preset.name(), getActiveTools().size());
    }

    /**
     * 获取所有工具组状态
     *
     * @return 组名 -> 是否激活
     */
    public Map<String, Boolean> getGroupStatus() {
        Map<String, Boolean> status = new LinkedHashMap<>();
        groups.forEach((name, group) -> status.put(name, group.isActive()));
        return status;
    }

    /**
     * 获取工具组信息列表
     *
     * @return 工具组信息
     */
    public List<GroupInfo> getGroupInfoList() {
        return groups.values().stream()
                .map(g -> new GroupInfo(
                        g.getName(),
                        g.getDescription(),
                        g.size(),
                        g.isActive()
                ))
                .collect(Collectors.toList());
    }

    /**
     * 检查工具是否属于某个组
     *
     * @param toolName  工具名
     * @param groupName 组名
     * @return 是否属于该组
     */
    public boolean isInGroup(String toolName, String groupName) {
        ToolGroup group = groups.get(groupName);
        return group != null && group.containsTool(toolName);
    }

    /**
     * 根据工具名查找所属组
     *
     * @param toolName 工具名
     * @return 组名列表（可能属于多个组）
     */
    public List<String> findGroupsByTool(String toolName) {
        return groups.values().stream()
                .filter(g -> g.containsTool(toolName))
                .map(ToolGroup::getName)
                .collect(Collectors.toList());
    }

    /**
     * 获取工具组
     *
     * @param groupName 组名
     * @return 工具组
     */
    public ToolGroup getGroup(String groupName) {
        return groups.get(groupName);
    }

    /**
     * 获取所有工具组名称
     *
     * @return 组名列表
     */
    public List<String> getGroupNames() {
        return new ArrayList<>(groups.keySet());
    }

    /**
     * 重置所有组到默认状态
     */
    public void resetAll() {
        groups.values().forEach(ToolGroup::reset);
        log.info("Reset all tool groups to default state");
    }

    /**
     * 激活所有组
     */
    public void activateAll() {
        groups.values().forEach(ToolGroup::activate);
        log.info("Activated all tool groups");
    }

    /**
     * 禁用所有组
     */
    public void deactivateAll() {
        groups.values().forEach(ToolGroup::deactivate);
        log.info("Deactivated all tool groups");
    }

    /**
     * 获取激活的工具数量
     */
    public int getActiveToolCount() {
        return getActiveTools().size();
    }

    /**
     * 获取总工具数量
     */
    public int getTotalToolCount() {
        return allTools.size();
    }

    /**
     * 工具组信息
     */
    public record GroupInfo(
            String name,
            String description,
            int toolCount,
            boolean active
    ) {
        @Override
        public String toString() {
            return String.format("%s: %d tools [%s]", name, toolCount, active ? "ON" : "OFF");
        }
    }
}

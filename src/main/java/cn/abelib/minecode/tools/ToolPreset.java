package cn.abelib.minecode.tools;

/**
 * 工具预设模式
 *
 * <p>定义常用的工具组合，便于快速切换。
 *
 * @author Abel
 */
public enum ToolPreset {

    /**
     * 只读模式 - 文件读取 + 搜索
     */
    READ_ONLY("read_file", "glob", "grep"),

    /**
     * 标准模式 - 文件 + 搜索 + 子代理
     */
    STANDARD("read_file", "write_file", "edit_file", "glob", "grep", "agent"),

    /**
     * 安全编辑模式 - 文件操作 + 搜索（无 bash）
     */
    SAFE_EDIT("read_file", "write_file", "edit_file", "glob", "grep"),

    /**
     * 完全访问模式 - 所有工具
     */
    FULL_ACCESS("read_file", "write_file", "edit_file", "glob", "grep", "bash", "agent"),

    /**
     * 最小模式 - 只有搜索
     */
    MINIMAL("glob", "grep"),

    /**
     * 搜索模式 - 搜索 + 子代理
     */
    SEARCH("glob", "grep", "agent");

    private final String[] toolNames;

    ToolPreset(String... toolNames) {
        this.toolNames = toolNames;
    }

    /**
     * 获取工具名称列表
     */
    public String[] getToolNames() {
        return toolNames;
    }

    /**
     * 获取描述
     */
    public String getDescription() {
        return switch (this) {
            case READ_ONLY -> "只读模式：文件读取 + 搜索工具";
            case STANDARD -> "标准模式：文件操作 + 搜索 + 子代理";
            case SAFE_EDIT -> "安全编辑：文件操作 + 搜索（无命令执行）";
            case FULL_ACCESS -> "完全访问：所有工具";
            case MINIMAL -> "最小模式：只有搜索工具";
            case SEARCH -> "搜索模式：搜索 + 子代理";
        };
    }
}

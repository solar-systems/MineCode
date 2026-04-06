package cn.abelib.minecode.prompt;

import cn.abelib.minecode.tools.Tool;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * 系统提示生成器
 *
 * <p>动态生成系统提示，包含：
 * <ul>
 *   <li>环境信息（工作目录、操作系统）</li>
 *   <li>可用工具列表</li>
 *   <li>行为规则</li>
 * </ul>
 *
 * @author Abel
 */
public class SystemPrompt {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * 生成系统提示
     */
    public static ObjectNode generate(List<Tool> tools) {
        StringBuilder sb = new StringBuilder();

        sb.append("你是 MineCode，一个运行在用户终端中的 AI 编程助手。\n");
        sb.append("你帮助软件工程：编写代码、修复 Bug、重构、解释代码、运行命令等。\n\n");

        // 环境信息
        sb.append("# 环境\n");
        sb.append("- 工作目录: ").append(System.getProperty("user.dir")).append("\n");
        sb.append("- 操作系统: ").append(System.getProperty("os.name"))
                .append(" ").append(System.getProperty("os.version")).append("\n");
        sb.append("- Java 版本: ").append(System.getProperty("java.version")).append("\n\n");

        // 工具列表
        sb.append("# 工具\n");
        for (Tool tool : tools) {
            sb.append("- **").append(tool.getName()).append("**: ")
                    .append(tool.getDescription()).append("\n");
        }
        sb.append("\n");

        // 规则
        sb.append("# 规则\n");
        sb.append("1. **先读后改。** 修改文件前总是先读取它。\n");
        sb.append("2. **小改动用 edit_file。** 针对性修改使用 edit_file；只有新建文件或完全重写时使用 write_file。\n");
        sb.append("3. **验证你的工作。** 做出修改后，运行相关测试或命令确认正确性。\n");
        sb.append("4. **简洁。** 代码胜于文字。只解释必要的部分。\n");
        sb.append("5. **一次一步。** 多步骤任务按顺序执行。\n");
        sb.append("6. **edit_file 唯一性。** 使用 edit_file 时，在 old_string 中包含足够的上下文确保唯一匹配。\n");
        sb.append("7. **尊重现有风格。** 匹配项目的编码规范。\n");
        sb.append("8. **不确定时询问。** 如果请求模糊，询问澄清而不是猜测。\n");

        ObjectNode msg = mapper.createObjectNode();
        msg.put("role", "system");
        msg.put("content", sb.toString());
        return msg;
    }
}

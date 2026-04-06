package cn.abelib.minecode.context;

import cn.abelib.minecode.llm.LLMClient;
import cn.abelib.minecode.llm.LLMResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多层上下文压缩管理器
 *
 * <p>Claude Code 使用 4 层策略：
 * <ol>
 *   <li>HISTORY_SNIP - 裁剪旧工具输出为单行摘要</li>
 *   <li>Microcompact - LLM 驱动的旧对话摘要（缓存）</li>
 *   <li>CONTEXT_COLLAPSE - 接近硬限制时激进压缩</li>
 *   <li>Autocompact - 定期后台压缩</li>
 * </ol>
 *
 * <p>MineCode 实现 3 层压缩：
 * <ol>
 *   <li>tool_snip - 用截断版本替换冗长的工具结果</li>
 *   <li>summarize - LLM 驱动的旧对话摘要</li>
 *   <li>hard_collapse - 最后手段：丢弃摘要之外的所有内容</li>
 * </ol>
 *
 * @author Abel
 */
public class ContextManager {
    private static final Logger log = LoggerFactory.getLogger(ContextManager.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final int maxTokens;

    // 层阈值（占 maxTokens 的比例）
    private final int snipAt;        // 50%
    private final int summarizeAt;   // 70%
    private final int collapseAt;    // 90%

    public ContextManager(int maxTokens) {
        this.maxTokens = maxTokens;
        this.snipAt = (int) (maxTokens * 0.50);
        this.summarizeAt = (int) (maxTokens * 0.70);
        this.collapseAt = (int) (maxTokens * 0.90);
    }

    /**
     * 根据需要应用压缩层
     *
     * @param messages 消息列表
     * @param llm      LLM 客户端（用于摘要）
     * @return 是否进行了压缩
     */
    public boolean maybeCompress(List<ObjectNode> messages, LLMClient llm) {
        int current = estimateTokens(messages);
        boolean compressed = false;

        // Layer 1: 裁剪冗长的工具输出
        if (current > snipAt) {
            if (snipToolOutputs(messages)) {
                compressed = true;
                current = estimateTokens(messages);
            }
        }

        // Layer 2: LLM 驱动的旧对话摘要
        if (current > summarizeAt && messages.size() > 10) {
            if (summarizeOld(messages, llm, 8)) {
                compressed = true;
                current = estimateTokens(messages);
            }
        }

        // Layer 3: 硬折叠 - 最后手段
        if (current > collapseAt && messages.size() > 4) {
            hardCollapse(messages, llm);
            compressed = true;
        }

        return compressed;
    }

    /**
     * Layer 1: 裁剪工具输出
     *
     * <p>将超过 1500 字符的工具结果截断为首尾各 3 行
     */
    private boolean snipToolOutputs(List<ObjectNode> messages) {
        boolean changed = false;

        for (ObjectNode msg : messages) {
            String role = msg.path("role").asText("");
            if (!"tool".equals(role)) {
                continue;
            }

            String content = msg.path("content").asText("");
            if (content.length() <= 1500) {
                continue;
            }

            String[] lines = content.split("\n");
            if (lines.length <= 6) {
                continue;
            }

            // 保留前 3 行 + 后 3 行
            StringBuilder snipped = new StringBuilder();
            for (int i = 0; i < 3 && i < lines.length; i++) {
                snipped.append(lines[i]).append("\n");
            }
            snipped.append("\n... (").append(lines.length).append(" 行，已裁剪以节省上下文) ...\n");
            for (int i = Math.max(0, lines.length - 3); i < lines.length; i++) {
                snipped.append(lines[i]).append("\n");
            }

            msg.put("content", snipped.toString());
            changed = true;
        }

        return changed;
    }

    /**
     * Layer 2: 摘要旧对话
     */
    private boolean summarizeOld(List<ObjectNode> messages, LLMClient llm, int keepRecent) {
        if (messages.size() <= keepRecent) {
            return false;
        }

        List<ObjectNode> old = new ArrayList<>(messages.subList(0, messages.size() - keepRecent));
        List<ObjectNode> tail = new ArrayList<>(messages.subList(messages.size() - keepRecent, messages.size()));

        String summary = getSummary(old, llm);

        messages.clear();

        ObjectNode summaryMsg = mapper.createObjectNode();
        summaryMsg.put("role", "user");
        summaryMsg.put("content", "[上下文已压缩 - 对话摘要]\n" + summary);
        messages.add(summaryMsg);

        ObjectNode ackMsg = mapper.createObjectNode();
        ackMsg.put("role", "assistant");
        ackMsg.put("content", "好的，我已经了解之前对话的上下文。");
        messages.add(ackMsg);

        messages.addAll(tail);
        return true;
    }

    /**
     * Layer 3: 硬折叠
     */
    private void hardCollapse(List<ObjectNode> messages, LLMClient llm) {
        List<ObjectNode> tail = messages.size() > 4
                ? new ArrayList<>(messages.subList(messages.size() - 4, messages.size()))
                : new ArrayList<>(messages);

        String summary = getSummary(new ArrayList<>(messages.subList(0, messages.size() - tail.size())), llm);

        messages.clear();

        ObjectNode summaryMsg = mapper.createObjectNode();
        summaryMsg.put("role", "user");
        summaryMsg.put("content", "[上下文强制重置]\n" + summary);
        messages.add(summaryMsg);

        ObjectNode ackMsg = mapper.createObjectNode();
        ackMsg.put("role", "assistant");
        ackMsg.put("content", "上下文已恢复。继续之前的工作。");
        messages.add(ackMsg);

        messages.addAll(tail);
    }

    /**
     * 获取摘要
     */
    private String getSummary(List<ObjectNode> messages, LLMClient llm) {
        String flat = flatten(messages);

        if (llm != null) {
            try {
                ObjectNode systemMsg = mapper.createObjectNode();
                systemMsg.put("role", "system");
                systemMsg.put("content", "将此对话压缩为简要摘要。" +
                        "保留：编辑的文件路径、关键决策、遇到的错误、当前任务状态。" +
                        "省略：冗长的命令输出、代码清单、多余的来回对话。");

                ObjectNode userMsg = mapper.createObjectNode();
                userMsg.put("role", "user");
                userMsg.put("content", flat.substring(0, Math.min(flat.length(), 15000)));

                LLMResponse response = llm.chat(List.of(systemMsg, userMsg), null, null);
                return response.content();
            } catch (Exception e) {
                log.warn("Failed to generate summary with LLM", e);
            }
        }

        // 回退：提取关键信息
        return extractKeyInfo(messages);
    }

    /**
     * 扁平化消息
     */
    private String flatten(List<ObjectNode> messages) {
        StringBuilder sb = new StringBuilder();
        for (ObjectNode msg : messages) {
            String role = msg.path("role").asText("?");
            String content = msg.path("content").asText("");
            if (!content.isEmpty()) {
                sb.append("[").append(role).append("] ")
                        .append(content.substring(0, Math.min(content.length(), 400)))
                        .append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 提取关键信息（无 LLM 时的回退方案）
     */
    private String extractKeyInfo(List<ObjectNode> messages) {
        java.util.Set<String> filesSeen = new java.util.HashSet<>();
        List<String> errors = new ArrayList<>();

        Pattern filePathPattern = Pattern.compile("[\\w./\\-]+\\.\\w{1,5}");

        for (ObjectNode msg : messages) {
            String content = msg.path("content").asText("");

            // 提取文件路径
            Matcher matcher = filePathPattern.matcher(content);
            while (matcher.find()) {
                filesSeen.add(matcher.group());
            }

            // 提取错误行
            for (String line : content.split("\n")) {
                if (line.toLowerCase().contains("error")) {
                    errors.add(line.substring(0, Math.min(line.length(), 150)));
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        if (!filesSeen.isEmpty()) {
            sb.append("涉及的文件: ").append(String.join(", ",
                    filesSeen.stream().limit(20).toList())).append("\n");
        }
        if (!errors.isEmpty()) {
            sb.append("遇到的错误: ").append(String.join("; ",
                    errors.stream().limit(5).toList())).append("\n");
        }

        return sb.length() > 0 ? sb.toString() : "(无可提取的上下文)";
    }

    /**
     * 估算 Token 数
     *
     * <p>粗略估算：混合中英文约 3.5 字符/Token
     */
    public static int estimateTokens(List<ObjectNode> messages) {
        int total = 0;
        for (ObjectNode msg : messages) {
            String content = msg.path("content").asText("");
            total += content.length() / 3;
            // 工具调用也计算
            if (msg.has("tool_calls")) {
                total += msg.path("tool_calls").toString().length() / 3;
            }
        }
        return total;
    }

    public int getMaxTokens() {
        return maxTokens;
    }
}

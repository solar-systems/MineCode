package cn.abelib.minecode.plan;

import cn.abelib.minecode.hook.Hook;
import cn.abelib.minecode.hook.HookEvent;
import cn.abelib.minecode.hook.PreReasoningEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 计划注入 Hook - 自动将计划上下文注入 Agent prompt
 *
 * <p>在每次 LLM 推理前，自动将当前计划进度添加到消息上下文中，
 * 让 LLM 了解任务执行进度。
 *
 * <p>使用示例：
 * <pre>{@code
 * PlanNotebook notebook = new PlanNotebook();
 * notebook.createPlanWithLLM("重构用户认证模块", llm);
 *
 * PlanHook planHook = new PlanHook(notebook);
 *
 * Agent agent = Agent.builder()
 *     .llm(llm)
 *     .hook(planHook)
 *     .build();
 *
 * // 每次 LLM 调用都会自动包含计划状态
 * }</pre>
 *
 * @author Abel
 */
public class PlanHook implements Hook {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final PlanNotebook notebook;
    private final boolean includeProgress;
    private final boolean onlyWhenInProgress;

    /**
     * 创建计划 Hook
     *
     * @param notebook 计划笔记本
     */
    public PlanHook(PlanNotebook notebook) {
        this(notebook, true, false);
    }

    /**
     * 创建计划 Hook（带选项）
     *
     * @param notebook           计划笔记本
     * @param includeProgress    是否包含进度条
     * @param onlyWhenInProgress 是否只在有进行中任务时注入
     */
    public PlanHook(PlanNotebook notebook, boolean includeProgress, boolean onlyWhenInProgress) {
        this.notebook = notebook;
        this.includeProgress = includeProgress;
        this.onlyWhenInProgress = onlyWhenInProgress;
    }

    @Override
    public HookEvent onEvent(HookEvent event) {
        if (event instanceof PreReasoningEvent preReasoning) {
            injectPlanContext(preReasoning);
        }
        return event;
    }

    private void injectPlanContext(PreReasoningEvent event) {
        if (notebook == null || notebook.getAllTasks().isEmpty()) {
            return;
        }

        // 检查是否只在有进行中任务时注入
        if (onlyWhenInProgress && notebook.getTasksByStatus(TaskStatus.IN_PROGRESS).isEmpty()) {
            return;
        }

        // 构建计划上下文
        StringBuilder context = new StringBuilder();
        context.append("\n\n--- 当前任务计划 ---\n");

        if (includeProgress) {
            context.append(notebook.getProgressSummary()).append("\n\n");
        }

        context.append(notebook.exportToText());
        context.append("\n--- 计划结束 ---\n");

        // 创建系统消息并注入
        ObjectNode systemMessage = mapper.createObjectNode();
        systemMessage.put("role", "system");
        systemMessage.put("content", "当前计划状态：\n" + notebook.getProgressSummary());
        event.getMessages().add(systemMessage);
    }

    @Override
    public int priority() {
        return 50;  // 中等优先级
    }

    @Override
    public String name() {
        return "PlanHook";
    }
}

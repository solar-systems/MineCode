package cn.abelib.minecode.plan;

import cn.abelib.minecode.llm.LLMClient;
import cn.abelib.minecode.llm.LLMResponse;
import cn.abelib.minecode.session.StateModule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 任务规划笔记本 - 复杂任务的分解与进度跟踪
 *
 * <p>核心功能：
 * <ul>
 *   <li>将复杂任务分解为可管理的子任务</li>
 *   <li>跟踪任务执行进度</li>
 *   <li>支持任务依赖关系</li>
 *   <li>导出进度文本供 LLM 理解</li>
 *   <li>与会话管理集成（实现 StateModule）</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * PlanNotebook notebook = new PlanNotebook();
 *
 * // LLM 自动分解
 * notebook.createPlanWithLLM("重构用户认证模块", llmClient);
 *
 * // 执行任务
 * while (notebook.hasNextTask()) {
 *     Task task = notebook.getNextPendingTask().get();
 *     notebook.markInProgress(task.getId());
 *
 *     String result = agent.chat(task.getDescription());
 *     notebook.markCompleted(task.getId(), result);
 * }
 * }</pre>
 *
 * @author Abel
 */
public class PlanNotebook implements StateModule {

    private static final Logger log = LoggerFactory.getLogger(PlanNotebook.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private String goal;
    private final List<Task> tasks = new ArrayList<>();
    private int taskCounter = 0;

    public PlanNotebook() {
    }

    public PlanNotebook(String goal) {
        this.goal = goal;
    }

    // ========== StateModule 实现 ==========

    @Override
    public String getModuleName() {
        return "planNotebook";
    }

    @Override
    public JsonNode saveState() {
        ObjectNode state = mapper.createObjectNode();
        state.put("goal", goal);
        state.put("taskCounter", taskCounter);

        ArrayNode tasksArray = state.putArray("tasks");
        for (Task task : tasks) {
            tasksArray.add(taskToJson(task));
        }

        return state;
    }

    @Override
    public void loadState(JsonNode state) {
        if (state == null) return;

        this.goal = state.path("goal").asText(null);
        this.taskCounter = state.path("taskCounter").asInt(0);

        tasks.clear();
        JsonNode tasksNode = state.path("tasks");
        if (tasksNode.isArray()) {
            for (JsonNode taskNode : tasksNode) {
                tasks.add(taskFromJson(taskNode));
            }
        }
    }

    // ========== 规划方法 ==========

    /**
     * 手动创建计划
     *
     * @param taskDescriptions 任务描述列表
     */
    public void createPlan(List<String> taskDescriptions) {
        tasks.clear();
        taskCounter = 0;

        for (String desc : taskDescriptions) {
            addTask(desc);
        }

        log.info("Created plan with {} tasks", tasks.size());
    }

    /**
     * LLM 自动分解任务
     *
     * @param goal 任务目标
     * @param llm  LLM 客户端
     */
    public void createPlanWithLLM(String goal, LLMClient llm) {
        this.goal = goal;
        tasks.clear();
        taskCounter = 0;

        String prompt = buildDecompositionPrompt(goal);

        try {
            // 构建消息列表
            ObjectNode userMessage = mapper.createObjectNode();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);

            List<ObjectNode> messages = new ArrayList<>();
            messages.add(userMessage);

            // 调用 LLM
            LLMResponse response = llm.chat(messages, null, null);
            parseTasksFromLLMResponse(response.content());
            log.info("LLM decomposed goal into {} tasks", tasks.size());
        } catch (Exception e) {
            log.error("Failed to decompose task with LLM", e);
            // 降级：创建单一任务
            addTask(goal);
        }
    }

    /**
     * 添加单个任务
     *
     * @param description 任务描述
     * @return 创建的任务
     */
    public Task addTask(String description) {
        return addTask(description, null);
    }

    /**
     * 添加带依赖的任务
     *
     * @param description  任务描述
     * @param dependencies 依赖任务 ID 列表
     * @return 创建的任务
     */
    public Task addTask(String description, List<String> dependencies) {
        taskCounter++;
        Task task = new Task("T" + taskCounter, description);
        if (dependencies != null && !dependencies.isEmpty()) {
            task.setDependencies(dependencies);
        }
        tasks.add(task);
        return task;
    }

    /**
     * 移除任务
     *
     * @param taskId 任务 ID
     * @return 是否成功
     */
    public boolean removeTask(String taskId) {
        return tasks.removeIf(t -> t.getId().equals(taskId));
    }

    // ========== 执行方法 ==========

    /**
     * 获取下一个待执行任务（考虑依赖）
     *
     * @return 任务（Optional）
     */
    public Optional<Task> getNextPendingTask() {
        return tasks.stream()
                .filter(Task::isPending)
                .filter(this::areDependenciesMet)
                .min(Comparator.comparingInt(Task::getPriority)
                        .thenComparing(t -> tasks.indexOf(t)));
    }

    /**
     * 标记任务为进行中
     *
     * @param taskId 任务 ID
     */
    public void markInProgress(String taskId) {
        Task task = getTask(taskId);
        if (task != null) {
            task.setStatus(TaskStatus.IN_PROGRESS);
            task.setStartedAt(LocalDateTime.now());
            log.info("Task {} marked as in progress", taskId);
        }
    }

    /**
     * 标记任务完成
     *
     * @param taskId 任务 ID
     * @param result 执行结果
     */
    public void markCompleted(String taskId, String result) {
        Task task = getTask(taskId);
        if (task != null) {
            task.setStatus(TaskStatus.COMPLETED);
            task.setResult(result);
            task.setCompletedAt(LocalDateTime.now());
            log.info("Task {} completed", taskId);
        }
    }

    /**
     * 标记任务失败
     *
     * @param taskId 任务 ID
     * @param error  错误信息
     */
    public void markFailed(String taskId, String error) {
        Task task = getTask(taskId);
        if (task != null) {
            task.setStatus(TaskStatus.FAILED);
            task.setError(error);
            task.setCompletedAt(LocalDateTime.now());
            log.warn("Task {} failed: {}", taskId, error);
        }
    }

    /**
     * 重置任务为待执行状态
     *
     * @param taskId 任务 ID
     */
    public void resetTask(String taskId) {
        Task task = getTask(taskId);
        if (task != null) {
            task.setStatus(TaskStatus.PENDING);
            task.setResult(null);
            task.setError(null);
            task.setStartedAt(null);
            task.setCompletedAt(null);
            log.info("Task {} reset to pending", taskId);
        }
    }

    // ========== 查询方法 ==========

    /**
     * 是否还有待执行任务
     */
    public boolean hasNextTask() {
        return getNextPendingTask().isPresent();
    }

    /**
     * 获取进度百分比
     */
    public int getProgressPercent() {
        if (tasks.isEmpty()) return 0;

        long completed = tasks.stream().filter(Task::isCompleted).count();
        return (int) (completed * 100 / tasks.size());
    }

    /**
     * 获取进度摘要
     */
    public String getProgressSummary() {
        long total = tasks.size();
        long completed = tasks.stream().filter(Task::isCompleted).count();
        long inProgress = tasks.stream().filter(Task::isInProgress).count();
        long pending = tasks.stream().filter(Task::isPending).count();
        long failed = tasks.stream().filter(Task::isFailed).count();

        int percent = getProgressPercent();
        String bar = buildProgressBar(percent);

        return String.format("进度: %s %d%% (%d/%d) | 进行中: %d | 待执行: %d | 失败: %d",
                bar, percent, completed, total, inProgress, pending, failed);
    }

    /**
     * 导出为文本（供 LLM 理解）
     */
    public String exportToText() {
        StringBuilder sb = new StringBuilder();

        sb.append("【任务计划】\n");
        if (goal != null && !goal.isEmpty()) {
            sb.append("目标：").append(goal).append("\n");
        }
        sb.append("\n");

        // 进度概览
        long total = tasks.size();
        long completed = tasks.stream().filter(Task::isCompleted).count();
        long inProgress = tasks.stream().filter(Task::isInProgress).count();
        long pending = tasks.stream().filter(Task::isPending).count();
        long failed = tasks.stream().filter(Task::isFailed).count();

        sb.append("【进度概览】\n");
        sb.append(String.format("总任务：%d | 已完成：%d | 进行中：%d | 待执行：%d | 失败：%d\n",
                total, completed, inProgress, pending, failed));
        sb.append(String.format("完成率：%d%%\n\n", getProgressPercent()));

        // 任务列表
        sb.append("【任务列表】\n");
        for (Task task : tasks) {
            sb.append(task.format()).append("\n");

            // 显示依赖
            if (task.getDependencies() != null && !task.getDependencies().isEmpty()) {
                sb.append("   依赖：").append(String.join(", ", task.getDependencies())).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 获取所有任务
     */
    public List<Task> getAllTasks() {
        return new ArrayList<>(tasks);
    }

    /**
     * 按状态获取任务
     */
    public List<Task> getTasksByStatus(TaskStatus status) {
        return tasks.stream()
                .filter(t -> t.getStatus() == status)
                .collect(Collectors.toList());
    }

    /**
     * 获取任务
     */
    public Task getTask(String taskId) {
        return tasks.stream()
                .filter(t -> t.getId().equals(taskId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取目标
     */
    public String getGoal() {
        return goal;
    }

    /**
     * 设置目标
     */
    public void setGoal(String goal) {
        this.goal = goal;
    }

    /**
     * 清空计划
     */
    public void clear() {
        tasks.clear();
        taskCounter = 0;
        goal = null;
    }

    // ========== 私有方法 ==========

    private boolean areDependenciesMet(Task task) {
        if (task.getDependencies() == null || task.getDependencies().isEmpty()) {
            return true;
        }

        for (String depId : task.getDependencies()) {
            Task dep = getTask(depId);
            if (dep == null || !dep.isCompleted()) {
                return false;
            }
        }
        return true;
    }

    private String buildDecompositionPrompt(String goal) {
        return """
                请将以下任务目标分解为具体的执行步骤。

                目标：%s

                要求：
                1. 每个步骤应该是独立的、可执行的任务
                2. 步骤之间应该有逻辑顺序
                3. 每个步骤用一行描述，不要编号

                请直接输出任务列表，每行一个任务，不要其他解释。
                """.formatted(goal);
    }

    private void parseTasksFromLLMResponse(String response) {
        if (response == null || response.isBlank()) return;

        String[] lines = response.split("\n");
        for (String line : lines) {
            String cleaned = line.trim()
                    .replaceAll("^\\d+[.、)\\s]+", "")  // 移除编号
                    .replaceAll("^[\\-•*]\\s*", "")     // 移除列表标记
                    .trim();

            if (!cleaned.isEmpty() && cleaned.length() > 2) {
                addTask(cleaned);
            }
        }
    }

    private String buildProgressBar(int percent) {
        int filled = percent / 10;
        int empty = 10 - filled;
        return "█".repeat(filled) + "░".repeat(empty);
    }

    private JsonNode taskToJson(Task task) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", task.getId());
        node.put("description", task.getDescription());
        node.put("status", task.getStatus().name());
        node.put("result", task.getResult());
        node.put("error", task.getError());
        node.put("priority", task.getPriority());

        if (task.getDependencies() != null && !task.getDependencies().isEmpty()) {
            ArrayNode deps = node.putArray("dependencies");
            task.getDependencies().forEach(deps::add);
        }

        return node;
    }

    private Task taskFromJson(JsonNode node) {
        Task task = new Task();
        task.setId(node.path("id").asText());
        task.setDescription(node.path("description").asText());
        task.setStatus(TaskStatus.valueOf(node.path("status").asText("PENDING")));
        task.setResult(node.path("result").asText(null));
        task.setError(node.path("error").asText(null));
        task.setPriority(node.path("priority").asInt(0));

        JsonNode deps = node.path("dependencies");
        if (deps.isArray()) {
            List<String> depList = new ArrayList<>();
            deps.forEach(d -> depList.add(d.asText()));
            task.setDependencies(depList);
        }

        return task;
    }
}

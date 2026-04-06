package cn.abelib.minecode.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plan 模块测试
 *
 * @author Abel
 */
class PlanTest {

    private PlanNotebook notebook;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        notebook = new PlanNotebook("Test Goal");
    }

    // ==================== TaskStatus Tests ====================

    @Test
    void testTaskStatus_values() {
        TaskStatus[] statuses = TaskStatus.values();

        assertEquals(4, statuses.length);
        assertTrue(Arrays.asList(statuses).contains(TaskStatus.PENDING));
        assertTrue(Arrays.asList(statuses).contains(TaskStatus.IN_PROGRESS));
        assertTrue(Arrays.asList(statuses).contains(TaskStatus.COMPLETED));
        assertTrue(Arrays.asList(statuses).contains(TaskStatus.FAILED));
    }

    @Test
    void testTaskStatus_iconAndLabel() {
        assertEquals("⚪", TaskStatus.PENDING.getIcon());
        assertEquals("待执行", TaskStatus.PENDING.getLabel());

        assertEquals("🔵", TaskStatus.IN_PROGRESS.getIcon());
        assertEquals("进行中", TaskStatus.IN_PROGRESS.getLabel());

        assertEquals("✅", TaskStatus.COMPLETED.getIcon());
        assertEquals("已完成", TaskStatus.COMPLETED.getLabel());

        assertEquals("❌", TaskStatus.FAILED.getIcon());
        assertEquals("已失败", TaskStatus.FAILED.getLabel());
    }

    @Test
    void testTaskStatus_isTerminal() {
        assertFalse(TaskStatus.PENDING.isTerminal());
        assertFalse(TaskStatus.IN_PROGRESS.isTerminal());
        assertTrue(TaskStatus.COMPLETED.isTerminal());
        assertTrue(TaskStatus.FAILED.isTerminal());
    }

    // ==================== Task Tests ====================

    @Test
    void testTask_creation() {
        Task task = new Task("T1", "Test task");

        assertEquals("T1", task.getId());
        assertEquals("Test task", task.getDescription());
        assertEquals(TaskStatus.PENDING, task.getStatus());
        assertTrue(task.isPending());
    }

    @Test
    void testTask_defaultConstructor() {
        Task task = new Task();

        assertNull(task.getId());
        assertEquals(TaskStatus.PENDING, task.getStatus());
        assertNotNull(task.getDependencies());
        assertEquals(0, task.getPriority());
        assertNotNull(task.getCreatedAt());
    }

    @Test
    void testTask_statusChecks() {
        Task task = new Task("T1", "Test");

        assertTrue(task.isPending());
        assertFalse(task.isInProgress());
        assertFalse(task.isCompleted());
        assertFalse(task.isFailed());
        assertFalse(task.isTerminal());

        task.setStatus(TaskStatus.IN_PROGRESS);
        assertFalse(task.isPending());
        assertTrue(task.isInProgress());

        task.setStatus(TaskStatus.COMPLETED);
        assertTrue(task.isCompleted());
        assertTrue(task.isTerminal());

        task.setStatus(TaskStatus.FAILED);
        assertTrue(task.isFailed());
        assertTrue(task.isTerminal());
    }

    @Test
    void testTask_dependencies() {
        Task task = new Task("T1", "Test");

        task.addDependency("T0");
        task.addDependency("T2");

        assertEquals(2, task.getDependencies().size());
        assertTrue(task.getDependencies().contains("T0"));
        assertTrue(task.getDependencies().contains("T2"));

        // 重复添加不会增加
        task.addDependency("T0");
        assertEquals(2, task.getDependencies().size());
    }

    @Test
    void testTask_setDependencies() {
        Task task = new Task("T1", "Test");

        task.setDependencies(Arrays.asList("T0", "T2"));

        assertEquals(2, task.getDependencies().size());
    }

    @Test
    void testTask_priority() {
        Task task = new Task("T1", "Test");

        assertEquals(0, task.getPriority());

        task.setPriority(10);
        assertEquals(10, task.getPriority());
    }

    @Test
    void testTask_resultAndError() {
        Task task = new Task("T1", "Test");

        task.setResult("Success result");
        assertEquals("Success result", task.getResult());

        task.setError("Error occurred");
        assertEquals("Error occurred", task.getError());
    }

    @Test
    void testTask_format() {
        Task task = new Task("T1", "Test task");

        String formatted = task.format();
        assertTrue(formatted.contains("T1"));
        assertTrue(formatted.contains("Test task"));
        assertTrue(formatted.contains("⚪")); // PENDING icon
    }

    @Test
    void testTask_toString() {
        Task task = new Task("T1", "Test task");

        String str = task.toString();
        assertTrue(str.contains("T1"));
        assertTrue(str.contains("Test task"));
    }

    // ==================== PlanNotebook Tests ====================

    @Test
    void testPlanNotebook_creation() {
        assertEquals("Test Goal", notebook.getGoal());
        assertTrue(notebook.getAllTasks().isEmpty());
    }

    @Test
    void testPlanNotebook_defaultConstructor() {
        PlanNotebook empty = new PlanNotebook();
        assertNull(empty.getGoal());
    }

    @Test
    void testPlanNotebook_setGoal() {
        notebook.setGoal("New Goal");
        assertEquals("New Goal", notebook.getGoal());
    }

    @Test
    void testPlanNotebook_createPlan() {
        notebook.createPlan(Arrays.asList("Task 1", "Task 2", "Task 3"));

        assertEquals(3, notebook.getAllTasks().size());
        assertEquals("Task 1", notebook.getAllTasks().get(0).getDescription());
    }

    @Test
    void testPlanNotebook_addTask() {
        Task task = notebook.addTask("New task");

        assertNotNull(task);
        assertEquals("T1", task.getId());
        assertEquals("New task", task.getDescription());
        assertEquals(1, notebook.getAllTasks().size());
    }

    @Test
    void testPlanNotebook_addTaskWithDependencies() {
        notebook.addTask("Task 1");
        notebook.addTask("Task 2", List.of("T1"));

        Task task2 = notebook.getTask("T2");
        assertNotNull(task2);
        assertEquals(1, task2.getDependencies().size());
        assertTrue(task2.getDependencies().contains("T1"));
    }

    @Test
    void testPlanNotebook_removeTask() {
        notebook.addTask("Task 1");
        notebook.addTask("Task 2");

        assertTrue(notebook.removeTask("T1"));
        assertEquals(1, notebook.getAllTasks().size());
        assertNull(notebook.getTask("T1"));
    }

    @Test
    void testPlanNotebook_removeTask_notFound() {
        assertFalse(notebook.removeTask("NON_EXISTENT"));
    }

    @Test
    void testPlanNotebook_getNextPendingTask() {
        notebook.addTask("Task 1");
        notebook.addTask("Task 2");

        Optional<Task> next = notebook.getNextPendingTask();

        assertTrue(next.isPresent());
        assertEquals("T1", next.get().getId());
    }

    @Test
    void testPlanNotebook_getNextPendingTask_withDependencies() {
        notebook.addTask("Task 1");
        notebook.addTask("Task 2", List.of("T1")); // T2 depends on T1

        // T1 is first because T2 has unmet dependency
        Optional<Task> next = notebook.getNextPendingTask();
        assertTrue(next.isPresent());
        assertEquals("T1", next.get().getId());

        // Complete T1
        notebook.markCompleted("T1", "Done");

        // Now T2 should be available
        next = notebook.getNextPendingTask();
        assertTrue(next.isPresent());
        assertEquals("T2", next.get().getId());
    }

    @Test
    void testPlanNotebook_markInProgress() {
        notebook.addTask("Task 1");

        notebook.markInProgress("T1");

        Task task = notebook.getTask("T1");
        assertEquals(TaskStatus.IN_PROGRESS, task.getStatus());
        assertNotNull(task.getStartedAt());
    }

    @Test
    void testPlanNotebook_markCompleted() {
        notebook.addTask("Task 1");

        notebook.markCompleted("T1", "Success result");

        Task task = notebook.getTask("T1");
        assertEquals(TaskStatus.COMPLETED, task.getStatus());
        assertEquals("Success result", task.getResult());
        assertNotNull(task.getCompletedAt());
    }

    @Test
    void testPlanNotebook_markFailed() {
        notebook.addTask("Task 1");

        notebook.markFailed("T1", "Error occurred");

        Task task = notebook.getTask("T1");
        assertEquals(TaskStatus.FAILED, task.getStatus());
        assertEquals("Error occurred", task.getError());
        assertNotNull(task.getCompletedAt());
    }

    @Test
    void testPlanNotebook_resetTask() {
        notebook.addTask("Task 1");
        notebook.markInProgress("T1");
        notebook.markCompleted("T1", "Done");

        notebook.resetTask("T1");

        Task task = notebook.getTask("T1");
        assertEquals(TaskStatus.PENDING, task.getStatus());
        assertNull(task.getResult());
        assertNull(task.getError());
        assertNull(task.getStartedAt());
        assertNull(task.getCompletedAt());
    }

    @Test
    void testPlanNotebook_hasNextTask() {
        assertFalse(notebook.hasNextTask());

        notebook.addTask("Task 1");
        assertTrue(notebook.hasNextTask());

        notebook.markCompleted("T1", "Done");
        assertFalse(notebook.hasNextTask());
    }

    @Test
    void testPlanNotebook_getProgressPercent() {
        assertEquals(0, notebook.getProgressPercent());

        notebook.addTask("Task 1");
        notebook.addTask("Task 2");
        assertEquals(0, notebook.getProgressPercent());

        notebook.markCompleted("T1", "Done");
        assertEquals(50, notebook.getProgressPercent());

        notebook.markCompleted("T2", "Done");
        assertEquals(100, notebook.getProgressPercent());
    }

    @Test
    void testPlanNotebook_getProgressSummary() {
        notebook.addTask("Task 1");
        notebook.addTask("Task 2");
        notebook.markCompleted("T1", "Done");

        String summary = notebook.getProgressSummary();

        assertTrue(summary.contains("50%"));
        assertTrue(summary.contains("1/2"));
        assertTrue(summary.contains("进行中"));
    }

    @Test
    void testPlanNotebook_exportToText() {
        notebook.addTask("Task 1");
        notebook.addTask("Task 2");
        notebook.markCompleted("T1", "Done");

        String text = notebook.exportToText();

        assertTrue(text.contains("【任务计划】"));
        assertTrue(text.contains("Test Goal"));
        assertTrue(text.contains("【进度概览】"));
        assertTrue(text.contains("【任务列表】"));
        assertTrue(text.contains("Task 1"));
        assertTrue(text.contains("Task 2"));
    }

    @Test
    void testPlanNotebook_getTasksByStatus() {
        notebook.addTask("Task 1");
        notebook.addTask("Task 2");
        notebook.addTask("Task 3");

        notebook.markInProgress("T1");
        notebook.markCompleted("T2", "Done");
        notebook.markFailed("T3", "Error");

        assertEquals(0, notebook.getTasksByStatus(TaskStatus.PENDING).size());
        assertEquals(1, notebook.getTasksByStatus(TaskStatus.IN_PROGRESS).size());
        assertEquals(1, notebook.getTasksByStatus(TaskStatus.COMPLETED).size());
        assertEquals(1, notebook.getTasksByStatus(TaskStatus.FAILED).size());
    }

    @Test
    void testPlanNotebook_clear() {
        notebook.addTask("Task 1");
        notebook.addTask("Task 2");

        notebook.clear();

        assertTrue(notebook.getAllTasks().isEmpty());
        assertNull(notebook.getGoal());
    }

    // ==================== StateModule Tests ====================

    @Test
    void testPlanNotebook_saveAndLoadState() {
        notebook.addTask("Task 1");
        notebook.addTask("Task 2");
        notebook.markCompleted("T1", "Done");

        // 保存状态
        JsonNode state = notebook.saveState();

        assertNotNull(state);
        assertEquals("Test Goal", state.path("goal").asText());
        assertEquals(2, state.path("tasks").size());

        // 创建新实例并加载
        PlanNotebook newNotebook = new PlanNotebook();
        newNotebook.loadState(state);

        assertEquals("Test Goal", newNotebook.getGoal());
        assertEquals(2, newNotebook.getAllTasks().size());
        assertEquals(TaskStatus.COMPLETED, newNotebook.getTask("T1").getStatus());
    }

    @Test
    void testPlanNotebook_loadState_null() {
        // 不应该抛出异常
        assertDoesNotThrow(() -> notebook.loadState(null));
    }

    @Test
    void testPlanNotebook_getModuleName() {
        assertEquals("planNotebook", notebook.getModuleName());
    }
}

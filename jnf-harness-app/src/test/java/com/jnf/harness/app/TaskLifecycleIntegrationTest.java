package com.jnf.harness.app;

import com.jnf.harness.domain.llm.model.valobj.FinishReason;
import com.jnf.harness.domain.llm.model.valobj.StreamChunk;
import com.jnf.harness.domain.llm.model.valobj.TokenUsage;
import com.jnf.harness.domain.task.HarnessExecutionService;
import com.jnf.harness.domain.task.HarnessTaskEntity;
import com.jnf.harness.domain.task.TaskStatus;
import com.jnf.harness.domain.task.TaskSubmitService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务主线验收（第 10 节 DoD）：自动执行 / 需审挂起 / 防绕过 / 审批后执行。
 */
@SpringBootTest(classes = JnfHarnessApplication.class,
        properties = {"spring.datasource.url=jdbc:h2:mem:tasktest;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"})
@Import(AgentApiIntegrationTest.FakeLlmConfiguration.class)
class TaskLifecycleIntegrationTest {

    @Autowired
    TaskSubmitService taskSubmitService;

    @Autowired
    HarnessExecutionService executionService;

    private static List<StreamChunk> text(String text) {
        return List.of(new StreamChunk.TextDelta(text),
                new StreamChunk.DoneChunk(FinishReason.STOP, TokenUsage.zero()));
    }

    private TaskStatus waitForTerminal(String taskId) throws InterruptedException {
        for (int i = 0; i < 150; i++) {
            TaskStatus status = taskSubmitService.get(taskId).status();
            if (status == TaskStatus.COMPLETED || status == TaskStatus.FAILED) {
                return status;
            }
            Thread.sleep(20);
        }
        return taskSubmitService.get(taskId).status();
    }

    @Test
    @DisplayName("无风险工具：免审直通 QUEUED → RUNNING → COMPLETED")
    void auto_path_completes() throws Exception {
        AgentApiIntegrationTest.FakeLlmConfiguration.SCRIPTS.add(text("任务完成"));
        HarnessTaskEntity task = taskSubmitService.submit("task-session-1", "做个无风险任务", List.of());

        assertEquals(TaskStatus.COMPLETED, waitForTerminal(task.taskId()));
    }

    @Test
    @DisplayName("风险工具：PENDING_APPROVAL → 防绕过拒绝 → 审批后执行完成")
    void approval_path_and_no_bypass() throws Exception {
        AgentApiIntegrationTest.FakeLlmConfiguration.SCRIPTS.add(text("写完了"));
        HarnessTaskEntity task = taskSubmitService.submit("task-session-2", "写个文件", List.of("fs_write"));
        assertEquals(TaskStatus.PENDING_APPROVAL, task.status());

        // 防绕过：未审批直接执行必须拒绝；未知任务是参数错
        assertThrows(IllegalStateException.class, () -> executionService.executeSession(task.taskId()),
                "PENDING_APPROVAL 直接执行必须抛异常");
        assertThrows(IllegalArgumentException.class, () -> taskSubmitService.approve("nope-task-x"));

        HarnessTaskEntity approved = taskSubmitService.approve(task.taskId());
        assertTrue(approved.status() == TaskStatus.QUEUED || approved.status() == TaskStatus.RUNNING
                || approved.status() == TaskStatus.COMPLETED);
        assertEquals(TaskStatus.COMPLETED, waitForTerminal(task.taskId()));
    }
}

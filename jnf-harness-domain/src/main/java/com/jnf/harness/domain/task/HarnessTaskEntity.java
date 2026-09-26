package com.jnf.harness.domain.task;

import java.util.List;

/**
 * Harness 任务实体。
 */
public record HarnessTaskEntity(String taskId, String sessionId, String prompt,
                                List<String> enabledTools, TaskStatus status) {

    public HarnessTaskEntity {
        enabledTools = enabledTools == null ? List.of() : List.copyOf(enabledTools);
    }

    public HarnessTaskEntity withStatus(TaskStatus newStatus) {
        return new HarnessTaskEntity(taskId, sessionId, prompt, enabledTools, newStatus);
    }
}

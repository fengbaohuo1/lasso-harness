package com.jnf.harness.domain.task;

import java.util.UUID;

/**
 * 任务提交编排：submission 过滤 → permission 评估 → approval 决策 → queue 入队 → execution。
 */
public class TaskSubmitService {

    private final TaskSubmissionPolicyService submissionPolicy;
    private final PermissionPolicyService permissionPolicy;
    private final ApprovalPolicyService approvalPolicy;
    private final IHarnessTaskQueueRepository queue;
    private final HarnessExecutionService execution;

    public TaskSubmitService(TaskSubmissionPolicyService submissionPolicy,
                             PermissionPolicyService permissionPolicy,
                             ApprovalPolicyService approvalPolicy,
                             IHarnessTaskQueueRepository queue,
                             HarnessExecutionService execution) {
        this.submissionPolicy = submissionPolicy;
        this.permissionPolicy = permissionPolicy;
        this.approvalPolicy = approvalPolicy;
        this.queue = queue;
        this.execution = execution;
    }

    /** 提交：免审 → QUEUED + 立即执行；需审 → PENDING_APPROVAL（等 approve）。 */
    public HarnessTaskEntity submit(String sessionId, String prompt, java.util.List<String> enabledTools) {
        HarnessTaskEntity cleaned = submissionPolicy.filter(new HarnessTaskEntity(
                UUID.randomUUID().toString(), sessionId, prompt, enabledTools, TaskStatus.CREATED));
        PermissionPolicyService.PermissionAssessment assessment =
                permissionPolicy.assess(cleaned.enabledTools());
        boolean autoApprove = approvalPolicy.autoApprove(
                ApprovalPolicyService.Profile.WEB, assessment, false);

        queue.insert(cleaned);
        if (autoApprove) {
            queue.updateStatus(cleaned.taskId(), TaskStatus.QUEUED);
            execution.executeSession(cleaned.taskId());
        } else {
            queue.updateStatus(cleaned.taskId(), TaskStatus.PENDING_APPROVAL);
        }
        return queue.findById(cleaned.taskId()).orElse(cleaned);
    }

    /** 审批通过：PENDING_APPROVAL → QUEUED → 执行。 */
    public HarnessTaskEntity approve(String taskId) {
        HarnessTaskEntity task = queue.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        if (task.status() != TaskStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("仅 PENDING_APPROVAL 可审批，当前: " + task.status());
        }
        queue.updateStatus(taskId, TaskStatus.QUEUED);
        execution.executeSession(taskId);
        return queue.findById(taskId).orElse(task);
    }

    public HarnessTaskEntity get(String taskId) {
        return queue.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
    }
}

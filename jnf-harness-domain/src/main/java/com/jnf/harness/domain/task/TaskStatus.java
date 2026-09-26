package com.jnf.harness.domain.task;

/**
 * 任务状态机：CREATED → PENDING_APPROVAL → QUEUED → RUNNING → COMPLETED/FAILED。
 */
public enum TaskStatus {
    CREATED,
    PENDING_APPROVAL,
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED;

    /** 执行入口只接受 QUEUED；PENDING_APPROVAL 必须先审批（防绕过）。 */
    public boolean executable() {
        return this == QUEUED;
    }
}

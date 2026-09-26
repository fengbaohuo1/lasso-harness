package com.jnf.harness.domain.task.approval;

import java.time.Instant;

/**
 * 审批记录（修正项 R10：审批历史可追溯）。
 */
public record ApprovalRecordEntity(String id, String sessionId, String toolName, String argsJson,
                                   String verdict, Instant createdAt, Instant decidedAt) {

    public static final String VERDICT_PENDING = "PENDING";
    public static final String VERDICT_ALLOW_ONCE = "ALLOW_ONCE";
    public static final String VERDICT_ALLOW_SESSION = "ALLOW_SESSION";
    public static final String VERDICT_DENY = "DENY";
    public static final String VERDICT_CANCEL = "CANCEL";
    public static final String VERDICT_TIMEOUT = "TIMEOUT";
}

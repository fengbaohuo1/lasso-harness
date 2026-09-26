package com.jnf.harness.domain.session.event;

/**
 * 回合结束原因（归一）。
 */
public enum TurnEndReason {
    COMPLETED,
    ABORTED,
    ERROR,
    MAX_STEPS,
    MAX_TOKENS
}

package com.jnf.harness.domain.agent.model;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agent 生命周期状态机：Idle ↔ Running（携带取消信号与进度）→ Maintenance。
 */
public sealed interface Phase permits Phase.Idle, Phase.Running, Phase.Maintenance {

    record Idle() implements Phase {
    }

    record Running(AtomicBoolean abort, long turn, long step) implements Phase {
    }

    record Maintenance(String reason) implements Phase {
    }
}

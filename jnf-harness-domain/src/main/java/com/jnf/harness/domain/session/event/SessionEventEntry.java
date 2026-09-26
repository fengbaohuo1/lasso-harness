package com.jnf.harness.domain.session.event;

import java.util.Objects;

/**
 * 事件日志条目：seq（会话内自增）+ 事件。
 */
public record SessionEventEntry(long seq, SessionEvent event) {

    public SessionEventEntry {
        Objects.requireNonNull(event, "event");
        if (seq <= 0) {
            throw new IllegalArgumentException("seq 必须为正整数: " + seq);
        }
    }
}

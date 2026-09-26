package com.jnf.harness.domain.agent.service.compaction;

import com.jnf.harness.domain.session.event.SessionLog;

/**
 * 会话压缩引擎契约：上下文过长时把早期历史压缩为摘要事件。
 */
public interface CompactionEngine {

    /**
     * 尝试压缩；返回是否发生了压缩。
     */
    boolean compactIfNeeded(SessionLog log, long estimatedTokens, long thresholdTokens);
}

package com.jnf.harness.domain.agent.service.compaction;

import com.jnf.harness.domain.session.event.SessionEvent;
import com.jnf.harness.domain.session.event.SessionEventEntry;
import com.jnf.harness.domain.session.event.SessionLog;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 基础压缩引擎：保留最近约 1/4 的消息事件原文，更早的交给摘要器
 * （摘要器由宿主注入——可以是 LLM 总结，也可以是测试假件）。
 * <p>压缩产物是 {@link SessionEvent.Compacted} 事件，历史事件不删除（可追溯）。
 */
public class BasicCompactionEngine implements CompactionEngine {

    private final Function<List<SessionEvent>, String> summarizer;

    public BasicCompactionEngine(Function<List<SessionEvent>, String> summarizer) {
        this.summarizer = summarizer;
    }

    @Override
    public boolean compactIfNeeded(SessionLog log, long estimatedTokens, long thresholdTokens) {
        if (estimatedTokens < thresholdTokens) {
            return false;
        }
        List<SessionEventEntry> entries = log.entries();
        List<SessionEventEntry> messageEvents = entries.stream()
                .filter(e -> isMessageEvent(e.event()))
                .toList();
        // 只压缩"消息类"事件，保留最近 1/4 原文
        int keep = Math.max(1, messageEvents.size() / 4);
        int compactCount = messageEvents.size() - keep;
        if (compactCount <= 0) {
            return false;
        }
        List<SessionEvent> toSummarize = new ArrayList<>();
        List<Long> sourceSeqs = new ArrayList<>();
        for (int i = 0; i < compactCount; i++) {
            toSummarize.add(messageEvents.get(i).event());
            sourceSeqs.add(messageEvents.get(i).seq());
        }
        String summary = summarizer.apply(toSummarize);
        log.append(new SessionEvent.Compacted(summary == null ? "" : summary, sourceSeqs));
        return true;
    }

    private boolean isMessageEvent(SessionEvent event) {
        return event instanceof SessionEvent.UserMessage
                || event instanceof SessionEvent.AssistantMessage
                || event instanceof SessionEvent.ToolCall
                || event instanceof SessionEvent.ToolResult;
    }
}

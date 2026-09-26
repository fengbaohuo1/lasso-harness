package com.jnf.harness.domain.session.event;

import com.jnf.harness.model.Message;
import com.jnf.harness.model.MessageSource;
import com.jnf.harness.model.ToolCallBlock;
import com.jnf.harness.model.ToolResultBlock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 会话事件日志：内存追加式 WAL。
 * <p>append 语义：分配会话内自增 seq；{@link #deriveMessages()} 由事件投影出对话消息
 * （压缩事件 Compacted 会用摘要替换其 sourceSeqs 范围的历史）。
 */
public class SessionLog {

    private final List<SessionEventEntry> entries = new ArrayList<>();

    /** 追加事件，返回带 seq 的条目。 */
    public synchronized SessionEventEntry append(SessionEvent event) {
        SessionEventEntry entry = new SessionEventEntry(entries.size() + 1L, event);
        entries.add(entry);
        return entry;
    }

    /** 批量追加，返回条目列表。 */
    public synchronized List<SessionEventEntry> appendBatch(List<SessionEvent> events) {
        List<SessionEventEntry> result = new ArrayList<>(events.size());
        for (SessionEvent event : events) {
            result.add(append(event));
        }
        return result;
    }

    /** 全量条目快照（只读）。 */
    public synchronized List<SessionEventEntry> entries() {
        return List.copyOf(entries);
    }

    public synchronized int size() {
        return entries.size();
    }

    /**
     * 由事件投影对话消息（供 LLM 上下文与回放）。
     * 规则：
     * <ul>
     *   <li>UserMessage → user 消息；AssistantMessage → assistant 消息；</li>
     *   <li>ToolCall/ToolResult → 成对的 assistant(tool_call) 与 tool 消息；</li>
     *   <li>AssistantChunk/Step/Turn 事件不进消息（由 AssistantMessage 收口）；</li>
     *   <li>Compacted(summary, sourceSeqs)：被概括的条目不再逐条投影，改为一条摘要消息。</li>
     * </ul>
     */
    public synchronized List<Message> deriveMessages() {
        Set<Long> compactedSeqs = new HashSet<>();
        List<CompactedSpan> spans = new ArrayList<>();
        for (SessionEventEntry entry : entries) {
            if (entry.event() instanceof SessionEvent.Compacted compacted) {
                compactedSeqs.addAll(compacted.sourceSeqs());
                spans.add(new CompactedSpan(compacted.summary(), compacted.sourceSeqs()));
            }
        }

        List<Message> messages = new ArrayList<>();
        Set<String> insertedSpans = new HashSet<>();
        for (SessionEventEntry entry : entries) {
            if (compactedSeqs.contains(entry.seq())) {
                // 该条目被压缩覆盖：在第一个位置插入一次摘要
                for (CompactedSpan span : spans) {
                    if (span.sourceSeqs().contains(entry.seq())
                            && insertedSpans.add(span.summary())) {
                        messages.add(Message.ofText(MessageSource.SYSTEM, "[Compacted History] " + span.summary()));
                    }
                }
                continue;
            }
            messages.addAll(toMessages(entry.event()));
        }
        return messages;
    }

    private record CompactedSpan(String summary, List<Long> sourceSeqs) {
    }

    private List<Message> toMessages(SessionEvent event) {
        List<Message> out = new ArrayList<>(2);
        if (event instanceof SessionEvent.UserMessage user) {
            out.add(Message.ofText(MessageSource.USER, user.text()));
        } else if (event instanceof SessionEvent.AssistantMessage assistant) {
            out.add(Message.ofText(MessageSource.ASSISTANT, assistant.text()));
        } else if (event instanceof SessionEvent.ToolCall call) {
            out.add(new Message(MessageSource.ASSISTANT, List.of(
                    ToolCallBlock.of(call.callId(), call.name(),
                            call.arguments() == null ? Map.of() : call.arguments()))));
        } else if (event instanceof SessionEvent.ToolResult result) {
            Message block = result.error()
                    ? new Message(MessageSource.TOOL,
                    List.of(ToolResultBlock.failure(result.callId(), result.name(), result.content())))
                    : new Message(MessageSource.TOOL,
                    List.of(ToolResultBlock.of(result.callId(), result.name(), result.content())));
            out.add(block);
        }
        return out;
    }
}

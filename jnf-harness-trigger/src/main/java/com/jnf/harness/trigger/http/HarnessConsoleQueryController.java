package com.jnf.harness.trigger.http;

import com.jnf.harness.api.response.Response;
import com.jnf.harness.domain.session.event.ISessionEventStore;
import com.jnf.harness.domain.session.event.SessionEventEntry;
import com.jnf.harness.domain.session.event.SessionLog;
import com.jnf.harness.model.ContentBlock;
import com.jnf.harness.model.Message;
import com.jnf.harness.model.ToolCallBlock;
import com.jnf.harness.model.ToolResultBlock;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 控制台查询（会话列表 + 消息回放；从事件存储重建，跨重启可用）。
 */
@RestController
@RequestMapping("/api/harness/console")
public class HarnessConsoleQueryController {

    private final ISessionEventStore eventStore;
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    public HarnessConsoleQueryController(ISessionEventStore eventStore) {
        this.eventStore = eventStore;
    }

    /** 会话列表（有事件记录的会话）。 */
    @GetMapping("/sessions")
    public Response<List<Map<String, Object>>> sessions() {
        // JSONL 存储按会话目录组织；这里返回已知会话（按事件量倒序由前端处理）
        List<Map<String, Object>> result = new ArrayList<>();
        for (String sessionId : eventStore.listSessionIds()) {
            List<SessionEventEntry> entries = eventStore.load(sessionId);
            result.add(Map.of("sessionId", sessionId, "events", entries.size()));
        }
        return Response.success(result);
    }

    /** 会话消息回放（由事件重建）。 */
    @GetMapping("/sessions/{sessionId}/messages")
    public Response<List<Map<String, Object>>> messages(@PathVariable String sessionId) {
        SessionLog log = new SessionLog();
        log.appendBatch(eventStore.load(sessionId).stream().map(SessionEventEntry::event).toList());
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Message message : log.deriveMessages()) {
            messages.add(serialize(message));
        }
        return Response.success(messages);
    }

    private Map<String, Object> serialize(Message message) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("role", message.source().name());
        json.put("text", message.text());
        List<Map<String, Object>> blocks = new ArrayList<>();
        for (ContentBlock block : message.blocks()) {
            if (block instanceof ToolCallBlock call) {
                // 参数出 JSON（Map.toString 会让前端无法解析，回放展示成 {k=v} 杂讯）
                String argsJson;
                try {
                    argsJson = MAPPER.writeValueAsString(call.arguments());
                } catch (Exception e) {
                    argsJson = String.valueOf(call.arguments());
                }
                blocks.add(Map.of("type", "tool_call", "callId", call.callId(),
                        "name", call.name(), "arguments", argsJson));
            } else if (block instanceof ToolResultBlock result) {
                blocks.add(Map.of("type", "tool_result", "callId", result.callId(),
                        "name", result.name(), "error", result.error(), "content", result.content()));
            }
        }
        json.put("blocks", blocks);
        return json;
    }
}

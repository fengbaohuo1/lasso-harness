package com.jnf.harness.trigger.http;

import com.jnf.harness.api.response.Response;
import com.jnf.harness.domain.session.event.ISessionEventStore;
import com.jnf.harness.domain.session.event.SessionEvent;
import com.jnf.harness.domain.session.event.SessionEventEntry;
import com.jnf.harness.domain.session.event.SessionLog;
import com.jnf.harness.domain.session.meta.SessionMeta;
import com.jnf.harness.domain.session.meta.SessionMetaService;
import com.jnf.harness.model.ContentBlock;
import com.jnf.harness.model.Message;
import com.jnf.harness.model.ToolCallBlock;
import com.jnf.harness.model.ToolResultBlock;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 控制台查询（会话列表 + 消息回放；从事件存储重建，跨重启可用）。
 * <p>F3 会话管理：重命名（PATCH）/ 软删（DELETE，事件保留可审计）；
 * F6 用量：usage 端点由 Usage 事件聚合（重启后仍准确）。
 */
@RestController
@RequestMapping("/api/harness/console")
public class HarnessConsoleQueryController {

    private final ISessionEventStore eventStore;
    private final SessionMetaService sessionMetaService;
    private final Path eventsRoot;
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    public HarnessConsoleQueryController(ISessionEventStore eventStore,
                                         SessionMetaService sessionMetaService,
                                         @org.springframework.beans.factory.annotation.Value(
                                                 "${harness.events.dir:./data/events}") String eventsRoot) {
        this.eventStore = eventStore;
        this.sessionMetaService = sessionMetaService;
        this.eventsRoot = Path.of(eventsRoot);
    }

    /** 会话列表（有事件记录的会话；已软删的过滤掉）。 */
    @GetMapping("/sessions")
    public Response<List<Map<String, Object>>> sessions() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String sessionId : eventStore.listSessionIds()) {
            SessionMeta meta = sessionMetaService.find(sessionId).orElse(null);
            if (meta != null && meta.deleted()) {
                continue;
            }
            List<SessionEventEntry> entries = eventStore.load(sessionId);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sessionId", sessionId);
            item.put("events", entries.size());        // 旧字段保留兼容
            item.put("eventCount", entries.size());
            item.put("title", meta == null ? null : meta.title());
            item.put("createdAt", fileTime(eventsRoot.resolve(sessionId), true));
            item.put("lastEventAt", fileTime(eventsRoot.resolve(sessionId).resolve("events.jsonl"), false));
            result.add(item);
        }
        // 最近活跃在前（前端可再分组/排序）
        result.sort((a, b) -> Long.compare((Long) b.get("lastEventAt"), (Long) a.get("lastEventAt")));
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

    /** 重命名会话（F3）。 */
    @PatchMapping("/sessions/{sessionId}")
    public Response<Map<String, Object>> rename(@PathVariable String sessionId,
                                                @RequestBody Map<String, Object> body) {
        Object title = body.get("title");
        try {
            SessionMeta meta = sessionMetaService.rename(sessionId, title == null ? null : String.valueOf(title));
            return Response.success(Map.of("sessionId", meta.sessionId(), "title", meta.title()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Response.invalidArgument(e.getMessage());
        }
    }

    /** 软删会话（F3：事件文件保留可审计，列表过滤）。 */
    @DeleteMapping("/sessions/{sessionId}")
    public Response<Map<String, Object>> delete(@PathVariable String sessionId) {
        SessionMeta meta = sessionMetaService.softDelete(sessionId);
        return Response.success(Map.of("sessionId", meta.sessionId(), "deleted", meta.deleted()));
    }

    /** 会话 token 用量（F6：由 Usage 事件聚合，事件里没有 usage 时全 0）。 */
    @GetMapping("/sessions/{sessionId}/usage")
    public Response<Map<String, Object>> usage(@PathVariable String sessionId) {
        long promptTokens = 0;
        long completionTokens = 0;
        long turns = 0;
        for (SessionEventEntry entry : eventStore.load(sessionId)) {
            SessionEvent event = entry.event();
            if (event instanceof SessionEvent.Usage usage) {
                promptTokens += usage.promptTokens();
                completionTokens += usage.completionTokens();
            } else if (event instanceof SessionEvent.TurnStart) {
                turns++;
            }
        }
        return Response.success(Map.of(
                "promptTokens", promptTokens,
                "completionTokens", completionTokens,
                "totalTokens", promptTokens + completionTokens,
                "turns", turns));
    }

    /** 目录/文件时间：目录取创建时间（兜底最近修改），文件取最近修改；无文件返回 0。 */
    private long fileTime(Path path, boolean creation) {
        try {
            if (creation && Files.isDirectory(path)) {
                FileTime created = (FileTime) Files.getAttribute(path, "creationTime");
                return created.toMillis();
            }
            return Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : 0L;
        } catch (IOException e) {
            throw new UncheckedIOException("读取时间失败: " + path, e);
        }
    }

    private Map<String, Object> serialize(Message message) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("role", message.source().name());
        json.put("text", message.text());
        List<Map<String, Object>> blocks = new ArrayList<>();
        for (ContentBlock block : message.blocks()) {
            if (block instanceof ToolCallBlock call) {
                // 参数出 JSON（Map.toString 会让前端解析失败、展示成 {k=v} 杂讯）
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

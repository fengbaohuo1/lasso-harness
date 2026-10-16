package com.jnf.harness.trigger.http;

import com.jnf.harness.api.response.Response;
import com.jnf.harness.domain.session.event.ISessionEventStore;
import com.jnf.harness.domain.session.event.SessionEvent;
import com.jnf.harness.domain.session.event.SessionEventEntry;
import com.jnf.harness.domain.session.event.SessionLog;
import com.jnf.harness.domain.session.meta.SessionMeta;
import com.jnf.harness.domain.session.meta.SessionMetaService;
import com.jnf.harness.model.ContentBlock;
import com.jnf.harness.model.ImageAttachmentRef;
import com.jnf.harness.model.ImageBlock;
import com.jnf.harness.model.Message;
import com.jnf.harness.model.MessageSource;
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
    private final com.jnf.harness.domain.agent.service.compaction.CompactionEngine compactionEngine;
    /** P11-5：上下文构成端点要按当前注册段算系统提示词占用。 */
    private final com.jnf.harness.domain.agent.service.SystemPromptAssembler promptAssembler;
    /** P11-14：回放用户消息里的图片附件（事件只存引用，需要还原成 data URI）。 */
    private final com.jnf.harness.domain.runtime.attachment.IAttachmentStore attachmentStore;
    private final Path eventsRoot;
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    public HarnessConsoleQueryController(ISessionEventStore eventStore,
                                         SessionMetaService sessionMetaService,
                                         com.jnf.harness.domain.agent.service.compaction.CompactionEngine compactionEngine,
                                         com.jnf.harness.domain.agent.service.SystemPromptAssembler promptAssembler,
                                         com.jnf.harness.domain.runtime.attachment.IAttachmentStore attachmentStore,
                                         @org.springframework.beans.factory.annotation.Value(
                                                 "${harness.events.dir:./data/events}") String eventsRoot) {
        this.eventStore = eventStore;
        this.sessionMetaService = sessionMetaService;
        this.compactionEngine = compactionEngine;
        this.promptAssembler = promptAssembler;
        this.attachmentStore = attachmentStore;
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
            item.put("workspaceCode", meta == null ? null : meta.workspaceCode());
            item.put("presetCode", meta == null ? null : meta.presetCode());
            item.put("reasoningEffort", meta == null ? null : meta.reasoningEffort());
            item.put("createdAt", fileTime(eventsRoot.resolve(sessionId), true));
            item.put("lastEventAt", fileTime(eventsRoot.resolve(sessionId).resolve("events.jsonl"), false));
            result.add(item);
        }
        // 最近活跃在前（前端可再分组/排序）
        result.sort((a, b) -> Long.compare((Long) b.get("lastEventAt"), (Long) a.get("lastEventAt")));
        return Response.success(result);
    }

    /** 会话消息回放（由事件重建；P9-8：assistant 消息附思考内容）。 */
    @GetMapping("/sessions/{sessionId}/messages")
    public Response<List<Map<String, Object>>> messages(@PathVariable String sessionId) {
        SessionLog log = new SessionLog();
        List<SessionEventEntry> entries = eventStore.load(sessionId);
        log.appendBatch(entries.stream().map(SessionEventEntry::event).toList());
        // P9-8：按 (turn,step) 聚合思考内容；AssistantMessage 事件序 ↔ 助手文本消息序一一对应
        //（压缩覆盖的条目跳过；ToolCall 事件虽是 assistant 源但不算文本消息）
        java.util.Set<Long> compactedSeqs = new java.util.HashSet<>();
        for (SessionEventEntry entry : entries) {
            if (entry.event() instanceof SessionEvent.Compacted compacted) {
                compactedSeqs.addAll(compacted.sourceSeqs());
            }
        }
        Map<String, String> reasoningByStep = new LinkedHashMap<>();
        List<String> assistantKeys = new ArrayList<>();
        for (SessionEventEntry entry : entries) {
            if (compactedSeqs.contains(entry.seq())) {
                continue;
            }
            SessionEvent event = entry.event();
            if (event instanceof SessionEvent.ReasoningChunk chunk) {
                reasoningByStep.merge(chunk.turn() + ":" + chunk.step(), chunk.text(), String::concat);
            } else if (event instanceof SessionEvent.AssistantMessage assistant) {
                assistantKeys.add(assistant.turn() + ":" + assistant.step());
            }
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        int assistantIndex = 0;
        for (Message message : log.deriveMessages()) {
            Map<String, Object> json = serialize(message);
            boolean textOnly = message.source() == com.jnf.harness.model.MessageSource.ASSISTANT
                    && message.blocks().stream().noneMatch(b -> b instanceof ToolCallBlock);
            if (textOnly && assistantIndex < assistantKeys.size()) {
                String reasoning = reasoningByStep.get(assistantKeys.get(assistantIndex));
                if (reasoning != null && !reasoning.isBlank()) {
                    json.put("reasoning", reasoning);
                }
                assistantIndex++;
            }
            messages.add(json);
        }
        return Response.success(messages);
    }

    /** P9：会话设置（工作区/智能体预设/思考强度）。 */
    @GetMapping("/sessions/{sessionId}/settings")
    public Response<Map<String, Object>> sessionSettings(@PathVariable String sessionId) {
        SessionMeta meta = sessionMetaService.find(sessionId)
                .orElseGet(() -> new SessionMeta(sessionId, null, false, System.currentTimeMillis()));
        return Response.success(settingsJson(meta));
    }

    /** P9：更新会话设置（合并语义：缺省字段沿用原值）。 */
    @PatchMapping("/sessions/{sessionId}/settings")
    public Response<Map<String, Object>> updateSessionSettings(@PathVariable String sessionId,
                                                               @RequestBody Map<String, Object> body) {
        try {
            SessionMeta meta = sessionMetaService.updateSettings(sessionId,
                    strOf(body.get("workspaceCode")), strOf(body.get("presetCode")),
                    strOf(body.get("reasoningEffort")));
            return Response.success(settingsJson(meta));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Response.invalidArgument(e.getMessage());
        }
    }

    private static String strOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Map<String, Object> settingsJson(SessionMeta meta) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("sessionId", meta.sessionId());
        json.put("workspaceCode", meta.workspaceCode());
        json.put("presetCode", meta.presetCode());
        json.put("reasoningEffort", meta.reasoningEffort());
        return json;
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

    /** P8-1：强制压缩会话（/compact 指令）——Compacted 事件落盘、历史保留可溯。 */
    @org.springframework.web.bind.annotation.PostMapping("/sessions/{sessionId}/compact")
    public Response<Map<String, Object>> compact(@PathVariable String sessionId) {
        SessionLog log = new SessionLog();
        log.appendBatch(eventStore.load(sessionId).stream().map(SessionEventEntry::event).toList());
        var persisting = new com.jnf.harness.domain.session.event.PersistingSessionLog(
                sessionId, log, eventStore);
        // 强制触发：把估计值抬到阈值之上（引擎自身按消息事件量决定压缩范围）
        int before = log.entries().size();
        boolean compacted = compactionEngine.compactIfNeeded(persisting.log(), Long.MAX_VALUE / 4, 1);
        if (compacted) {
            persisting.flushNewEntries(before);
        }
        return Response.success(Map.of("sessionId", sessionId, "compacted", compacted));
    }

    /** 会话 token 用量（F6：由 Usage 事件聚合，事件里没有 usage 时全 0）。
     * <p>C3 状态栏扩展：steps/toolCalls/contextTokens/compactions 亦全部事件聚合（重启后仍准确）。
     * <p>P11-5：补 cachedTokens/cacheHitRate/elapsedMs/tokensPerSecond。
     * <p>注意区分两个「上下文」概念：{@code contextTokens} 是最近一次 LLM 调用的上下文占用估计，
     * {@code contextUsedTokens} 才是"用户消息 + 系统提示词 + 压缩摘要"这种真实构成（见 /context）。 */
    @GetMapping("/sessions/{sessionId}/usage")
    public Response<Map<String, Object>> usage(@PathVariable String sessionId) {
        long promptTokens = 0;
        long completionTokens = 0;
        long cachedTokens = 0;
        long turns = 0;
        long steps = 0;
        long toolCalls = 0;
        long compactions = 0;
        long contextTokens = 0;
        long lastElapsedMs = 0;
        long lastCompletionTokens = 0;
        for (SessionEventEntry entry : eventStore.load(sessionId)) {
            SessionEvent event = entry.event();
            if (event instanceof SessionEvent.Usage usage) {
                promptTokens += usage.promptTokens();
                completionTokens += usage.completionTokens();
                cachedTokens += usage.cachedTokens();
                contextTokens = usage.promptTokens(); // 最近一次 LLM 调用的上下文占用估计
                lastElapsedMs = usage.elapsedMs();
                lastCompletionTokens = usage.completionTokens();
            } else if (event instanceof SessionEvent.TurnStart) {
                turns++;
            } else if (event instanceof SessionEvent.StepEnd) {
                steps++;
            } else if (event instanceof SessionEvent.ToolCall) {
                toolCalls++;
            } else if (event instanceof SessionEvent.Compacted) {
                compactions++;
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("promptTokens", promptTokens);
        body.put("completionTokens", completionTokens);
        body.put("totalTokens", promptTokens + completionTokens);
        body.put("cachedTokens", cachedTokens);
        body.put("cacheHitRate", promptTokens <= 0 ? 0d : (double) cachedTokens / (double) promptTokens);
        body.put("elapsedMs", lastElapsedMs);
        // 速度按最近一次调用算（累计平均会被首轮冷启动摊平）
        body.put("tokensPerSecond", lastElapsedMs <= 0 ? 0d
                : lastCompletionTokens * 1000d / lastElapsedMs);
        body.put("turns", turns);
        body.put("steps", steps);
        body.put("toolCalls", toolCalls);
        body.put("contextTokens", contextTokens);
        body.put("compactions", compactions);
        return Response.success(body);
    }

    /**
     * 上下文构成（P11-5）：把「本次请求真正送进模型的东西」拆开算清楚，
     * 供前端上下文面板显示真实占比，而不是拿一个笼统的 contextTokens 顶账。
     * <p>口径：system=系统提示词段（按当前注册段拼接后的字符估算）、summary=最近一次压缩摘要、
     * messages=用户与助手可见文本、tools=工具 schema JSON、reasoning=思考文本；
     * 估算一律沿用内核的 chars/4（与 ReactLoopAgent.estimateTokens 同口径）。
     */
    @GetMapping("/sessions/{sessionId}/context")
    public Response<Map<String, Object>> context(@PathVariable String sessionId) {
        List<SessionEventEntry> entries = eventStore.load(sessionId);
        long userChars = 0;
        long assistantChars = 0;
        long reasoningChars = 0;
        long toolResultChars = 0;
        long summaryChars = 0;
        List<Map<String, Object>> messages = new ArrayList<>();
        for (SessionEventEntry entry : entries) {
            SessionEvent event = entry.event();
            if (event instanceof SessionEvent.UserMessage user) {
                userChars += length(user.text());
                messages.add(contextItem("user", user.turn(), user.step(), user.text()));
            } else if (event instanceof SessionEvent.AssistantMessage assistant) {
                assistantChars += length(assistant.text());
                messages.add(contextItem("assistant", assistant.turn(), assistant.step(), assistant.text()));
            } else if (event instanceof SessionEvent.ReasoningChunk reasoning) {
                reasoningChars += length(reasoning.text());
            } else if (event instanceof SessionEvent.ToolResult tool) {
                toolResultChars += length(tool.content());
            } else if (event instanceof SessionEvent.Compacted compacted) {
                summaryChars = length(compacted.summary()); // 只认最近一次压缩的摘要
            }
        }
        long systemChars = promptAssembler.assemble().length();
        long estimate = (userChars + assistantChars + reasoningChars + toolResultChars + summaryChars
                + systemChars) / 4;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("estimatedTokens", estimate);
        body.put("systemTokens", systemChars / 4);
        body.put("summaryTokens", summaryChars / 4);
        body.put("userTokens", userChars / 4);
        body.put("assistantTokens", assistantChars / 4);
        body.put("reasoningTokens", reasoningChars / 4);
        body.put("toolResultTokens", toolResultChars / 4);
        body.put("messages", messages);
        return Response.success(body);
    }

    private static Map<String, Object> contextItem(String role, long turn, long step, String text) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("role", role);
        item.put("turn", turn);
        item.put("step", step);
        item.put("chars", text == null ? 0 : text.length());
        item.put("tokens", (text == null ? 0 : text.length()) / 4);
        item.put("preview", text == null ? "" : abbreviate(text));
        return item;
    }

    private static String abbreviate(String text) {
        String flat = text.replace('\n', ' ').trim();
        return flat.length() <= 80 ? flat : flat.substring(0, 80) + "…";
    }

    private static int length(String text) {
        return text == null ? 0 : text.length();
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
        // P11-14：用户消息的图片附件。事件里只存 ImageAttachmentRef（避免 JSONL 日志被 base64 撑爆），
        // 回放时经附件库还原成 data URI 才能在前端显示——不回传的话刷新后图片气泡是空的。
        if (message.source() == MessageSource.USER) {
            List<Map<String, Object>> attachments = new ArrayList<>();
            for (ContentBlock block : message.blocks()) {
                if (block instanceof ImageBlock image) {
                    ImageAttachmentRef ref = image.ref();
                    String dataUri = attachmentStore.toDataUri(ref.mediaType(), ref.dataRef());
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("mediaType", ref.mediaType());
                    item.put("dataRef", ref.dataRef());
                    item.put("data", dataUri == null ? "" : dataUri);
                    attachments.add(item);
                }
            }
            if (!attachments.isEmpty()) {
                json.put("attachments", attachments);
            }
        }
        return json;
    }
}

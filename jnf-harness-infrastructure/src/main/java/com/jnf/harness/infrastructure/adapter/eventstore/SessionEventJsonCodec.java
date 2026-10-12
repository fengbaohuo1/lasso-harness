package com.jnf.harness.infrastructure.adapter.eventstore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jnf.harness.domain.session.event.SessionEvent;
import com.jnf.harness.domain.session.event.SessionEventEntry;
import com.jnf.harness.domain.session.event.TurnEndReason;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SessionEvent ↔ JSON 编解码（显式类型标签，不用多态魔法）。
 * 形态：{"seq":1,"type":"user_message","turn":1,"step":1,"text":"..."}
 */
public final class SessionEventJsonCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SessionEventJsonCodec() {
    }

    public static String toJson(SessionEventEntry entry) throws com.fasterxml.jackson.core.JsonProcessingException {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("seq", entry.seq());
        SessionEvent event = entry.event();
        if (event instanceof SessionEvent.TurnStart e) {
            root.put("type", "turn_start");
            root.put("turn", e.turn());
        } else if (event instanceof SessionEvent.TurnEnd e) {
            root.put("type", "turn_end");
            root.put("turn", e.turn());
            root.put("reason", e.reason().name());
            root.put("detail", e.detail() == null ? "" : e.detail());
        } else if (event instanceof SessionEvent.StepStart e) {
            root.put("type", "step_start");
            root.put("turn", e.turn());
            root.put("step", e.step());
        } else if (event instanceof SessionEvent.StepEnd e) {
            root.put("type", "step_end");
            root.put("turn", e.turn());
            root.put("step", e.step());
        } else if (event instanceof SessionEvent.UserMessage e) {
            root.put("type", "user_message");
            root.put("turn", e.turn());
            root.put("step", e.step());
            root.put("text", e.text());
        } else if (event instanceof SessionEvent.AssistantChunk e) {
            root.put("type", "assistant_chunk");
            root.put("turn", e.turn());
            root.put("step", e.step());
            root.put("text", e.text());
        } else if (event instanceof SessionEvent.ReasoningChunk e) {
            root.put("type", "reasoning_chunk");
            root.put("turn", e.turn());
            root.put("step", e.step());
            root.put("text", e.text());
        } else if (event instanceof SessionEvent.AssistantMessage e) {
            root.put("type", "assistant_message");
            root.put("turn", e.turn());
            root.put("step", e.step());
            root.put("text", e.text());
        } else if (event instanceof SessionEvent.ToolCall e) {
            root.put("type", "tool_call");
            root.put("turn", e.turn());
            root.put("step", e.step());
            root.put("callId", e.callId());
            root.put("name", e.name());
            root.set("arguments", MAPPER.valueToTree(e.arguments()));
        } else if (event instanceof SessionEvent.ToolResult e) {
            root.put("type", "tool_result");
            root.put("turn", e.turn());
            root.put("step", e.step());
            root.put("callId", e.callId());
            root.put("name", e.name());
            root.put("error", e.error());
            root.put("content", e.content());
        } else if (event instanceof SessionEvent.Compacted e) {
            root.put("type", "compacted");
            root.put("summary", e.summary());
            ArrayNode seqs = root.putArray("sourceSeqs");
            e.sourceSeqs().forEach(seqs::add);
        } else if (event instanceof SessionEvent.Usage e) {
            root.put("type", "usage");
            root.put("turn", e.turn());
            root.put("step", e.step());
            root.put("promptTokens", e.promptTokens());
            root.put("completionTokens", e.completionTokens());
        }
        return MAPPER.writeValueAsString(root);
    }

    public static SessionEventEntry fromJson(String json) throws com.fasterxml.jackson.core.JsonProcessingException {
        JsonNode root = MAPPER.readTree(json);
        long seq = root.path("seq").asLong();
        String type = root.path("type").asText("");
        SessionEvent event = switch (type) {
            case "turn_start" -> new SessionEvent.TurnStart(root.path("turn").asLong());
            case "turn_end" -> new SessionEvent.TurnEnd(root.path("turn").asLong(),
                    TurnEndReason.valueOf(root.path("reason").asText("ERROR")),
                    root.path("detail").asText(""));
            case "step_start" -> new SessionEvent.StepStart(root.path("turn").asLong(), root.path("step").asLong());
            case "step_end" -> new SessionEvent.StepEnd(root.path("turn").asLong(), root.path("step").asLong());
            case "user_message" -> new SessionEvent.UserMessage(
                    root.path("turn").asLong(), root.path("step").asLong(), root.path("text").asText(""));
            case "assistant_chunk" -> new SessionEvent.AssistantChunk(
                    root.path("turn").asLong(), root.path("step").asLong(), root.path("text").asText(""));
            case "reasoning_chunk" -> new SessionEvent.ReasoningChunk(
                    root.path("turn").asLong(), root.path("step").asLong(), root.path("text").asText(""));
            case "assistant_message" -> new SessionEvent.AssistantMessage(
                    root.path("turn").asLong(), root.path("step").asLong(), root.path("text").asText(""));
            case "tool_call" -> new SessionEvent.ToolCall(
                    root.path("turn").asLong(), root.path("step").asLong(),
                    root.path("callId").asText(""), root.path("name").asText(""),
                    toMap(root.path("arguments")));
            case "tool_result" -> new SessionEvent.ToolResult(
                    root.path("turn").asLong(), root.path("step").asLong(),
                    root.path("callId").asText(""), root.path("name").asText(""),
                    root.path("error").asBoolean(false), root.path("content").asText(""));
            case "compacted" -> {
                List<Long> seqs = new ArrayList<>();
                root.path("sourceSeqs").forEach(n -> seqs.add(n.asLong()));
                yield new SessionEvent.Compacted(root.path("summary").asText(""), seqs);
            }
            case "usage" -> new SessionEvent.Usage(
                    root.path("turn").asLong(), root.path("step").asLong(),
                    root.path("promptTokens").asLong(), root.path("completionTokens").asLong());
            default -> throw new IllegalStateException("未知事件类型: " + type);
        };
        return new SessionEventEntry(seq, event);
    }

    private static Map<String, Object> toMap(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        node.fields().forEachRemaining(e -> map.put(e.getKey(), e.getValue().isNumber()
                ? e.getValue().numberValue() : e.getValue().asText()));
        return map;
    }
}

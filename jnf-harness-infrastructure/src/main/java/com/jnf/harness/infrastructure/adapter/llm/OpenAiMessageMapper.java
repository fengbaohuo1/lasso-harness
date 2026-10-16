package com.jnf.harness.infrastructure.adapter.llm;

import com.jnf.harness.model.ContentBlock;
import com.jnf.harness.model.Message;
import com.jnf.harness.model.MessageSource;
import com.jnf.harness.model.ToolCallBlock;
import com.jnf.harness.model.ToolResultBlock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 领域消息 → OpenAI 兼容协议 messages 数组。
 * <ul>
 *   <li>USER/ASSISTANT/SYSTEM → role + content 文本；</li>
 *   <li>ASSISTANT 带 ToolCallBlock → tool_calls；</li>
 *   <li>TOOL 来源 → role=tool + tool_call_id + content；</li>
 *   <li>USER 带 ImageBlock → content 由纯字符串升为 parts 数组（{@code text} + {@code image_url}）。</li>
 * </ul>
 * P11-4：图片需要 data URI，而领域消息里只有 {@link com.jnf.harness.model.ImageAttachmentRef} 引用，
 * 因此由调用方注入解析器（附件库 → data URI）；解析不出图片时该块被丢弃，绝不发半个坏 parts 给上游。
 */
public final class OpenAiMessageMapper {

    private OpenAiMessageMapper() {
    }

    /** 兼容入口：无图片解析器（图片块直接丢弃）。 */
    public static List<Map<String, Object>> toOpenAiMessages(List<Message> messages) {
        return toOpenAiMessages(messages, ref -> null);
    }

    /**
     * @param imageResolver 附件引用 → data URI（返回 null 表示不可用，该图片块被跳过）
     */
    public static List<Map<String, Object>> toOpenAiMessages(
            List<Message> messages, java.util.function.Function<com.jnf.harness.model.ImageAttachmentRef, String> imageResolver) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Message message : messages) {
            out.addAll(toOpenAiMessage(message, imageResolver));
        }
        return out;
    }

    private static List<Map<String, Object>> toOpenAiMessage(
            Message message, java.util.function.Function<com.jnf.harness.model.ImageAttachmentRef, String> imageResolver) {
        List<Map<String, Object>> out = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        List<Map<String, Object>> parts = new ArrayList<>();
        List<Map<String, Object>> toolCalls = new ArrayList<>();

        for (ContentBlock block : message.blocks()) {
            if (block instanceof com.jnf.harness.model.TextBlock t) {
                text.append(t.text());
                parts.add(Map.of("type", "text", "text", t.text()));
            } else if (block instanceof com.jnf.harness.model.ImageBlock image) {
                String dataUri = imageResolver == null ? null : imageResolver.apply(image.ref());
                if (dataUri != null && !dataUri.isBlank()) {
                    parts.add(Map.of("type", "image_url", "image_url", Map.of("url", dataUri)));
                }
            } else if (block instanceof ToolCallBlock call) {
                Map<String, Object> fn = new LinkedHashMap<>();
                fn.put("name", call.name());
                fn.put("arguments", jsonArguments(call.arguments()));
                Map<String, Object> toolCall = new LinkedHashMap<>();
                toolCall.put("id", call.callId());
                toolCall.put("type", "function");
                toolCall.put("function", fn);
                toolCalls.add(toolCall);
            } else if (block instanceof ToolResultBlock result) {
                Map<String, Object> toolMsg = new LinkedHashMap<>();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", result.callId());
                toolMsg.put("content", result.content());
                out.add(toolMsg);
            }
        }

        if (!toolCalls.isEmpty() || text.length() > 0 || message.source() != MessageSource.TOOL) {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", roleOf(message.source()));
            // 只在真的有图片时用 parts 形态，纯文本消息保持字符串形态（兼容所有 OpenAI 兼容网关）
            boolean hasImage = parts.stream().anyMatch(part -> "image_url".equals(part.get("type")));
            msg.put("content", hasImage ? parts : text.toString());
            if (!toolCalls.isEmpty()) {
                msg.put("tool_calls", toolCalls);
            }
            out.add(msg);
        }
        return out;
    }

    private static String roleOf(MessageSource source) {
        return switch (source) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case TOOL -> "tool";
        };
    }

    private static String jsonArguments(Map<String, Object> arguments) {
        // 参数 map → JSON 字符串（简单手写序列化：适配器层不引新依赖）
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(entry.getKey())).append("\":");
            Object value = entry.getValue();
            if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else {
                sb.append('"').append(escape(String.valueOf(value))).append('"');
            }
        }
        return sb.append('}').toString();
    }

    private static String escape(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

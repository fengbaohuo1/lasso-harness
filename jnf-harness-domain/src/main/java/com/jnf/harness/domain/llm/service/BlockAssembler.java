package com.jnf.harness.domain.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jnf.harness.domain.llm.model.valobj.FinishReason;
import com.jnf.harness.domain.llm.model.valobj.StreamChunk;
import com.jnf.harness.domain.llm.model.valobj.TokenUsage;
import com.jnf.harness.model.ToolCallBlock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流增量装配器：把 {@link StreamChunk} 序列装配为一次生成的完整结果
 * （文本 / 思考 / 工具调用（参数分片拼装）/ 用量 / 结束原因）。
 */
public class BlockAssembler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StringBuilder text = new StringBuilder();
    private final StringBuilder reasoning = new StringBuilder();
    private final Map<String, StringBuilder> toolArguments = new LinkedHashMap<>();
    private final Map<String, String> toolNames = new LinkedHashMap<>();
    private TokenUsage usage = TokenUsage.zero();
    private FinishReason finishReason = FinishReason.UNKNOWN;

    public void accept(StreamChunk chunk) {
        if (chunk instanceof StreamChunk.TextDelta delta) {
            text.append(delta.text());
        } else if (chunk instanceof StreamChunk.ReasoningDelta delta) {
            reasoning.append(delta.text());
        } else if (chunk instanceof StreamChunk.ToolCallDelta delta) {
            toolNames.putIfAbsent(delta.callId(), delta.name() == null ? "" : delta.name());
            if (delta.argumentsDelta() != null && !delta.argumentsDelta().isEmpty()) {
                toolArguments.computeIfAbsent(delta.callId(), k -> new StringBuilder())
                        .append(delta.argumentsDelta());
            } else {
                toolArguments.putIfAbsent(delta.callId(), new StringBuilder());
            }
        } else if (chunk instanceof StreamChunk.DoneChunk done) {
            usage = done.usage() == null ? usage : usage.plus(done.usage());
            finishReason = done.reason();
        }
    }

    public GenerationResult result() {
        List<ToolCallBlock> calls = new ArrayList<>();
        for (Map.Entry<String, String> entry : toolNames.entrySet()) {
            StringBuilder args = toolArguments.get(entry.getKey());
            calls.add(ToolCallBlock.of(entry.getKey(), entry.getValue(), parseArgs(args)));
        }
        return new GenerationResult(text.toString(), reasoning.toString(), calls, usage, finishReason);
    }

    private Map<String, Object> parseArgs(StringBuilder argsJson) {
        if (argsJson == null || argsJson.isEmpty()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(argsJson.toString(),
                    MAPPER.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        } catch (Exception e) {
            // 参数分片不完整/非法：原样放进 _raw 让上层感知
            return Map.of("_raw", argsJson.toString());
        }
    }

    /** 一次生成的装配结果。 */
    public record GenerationResult(String text, String reasoning, List<ToolCallBlock> toolCalls,
                                   TokenUsage usage, FinishReason finishReason) {
    }
}

package com.jnf.harness.domain.llm.model.valobj;

/**
 * 流式生成的增量块（sealed 封闭家族）。
 */
public sealed interface StreamChunk
        permits StreamChunk.TextDelta, StreamChunk.ReasoningDelta,
        StreamChunk.ToolCallDelta, StreamChunk.DoneChunk {

    /** 文本增量。 */
    record TextDelta(String text) implements StreamChunk {
    }

    /** 思考过程增量。 */
    record ReasoningDelta(String text) implements StreamChunk {
    }

    /**
     * 工具调用增量：argumentsDelta 是 JSON 参数的分片，由上层按 callId 累积拼装。
     */
    record ToolCallDelta(String callId, String name, String argumentsDelta) implements StreamChunk {
    }

    /** 结束块：归一的结束原因 + 用量。 */
    record DoneChunk(FinishReason reason, TokenUsage usage) implements StreamChunk {
    }
}

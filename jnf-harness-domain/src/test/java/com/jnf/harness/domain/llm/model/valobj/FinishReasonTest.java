package com.jnf.harness.domain.llm.model.valobj;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 结束原因归一：max_tokens 截断必须可识别（第 2 节 DoD）。
 */
class FinishReasonTest {

    @Test
    @DisplayName("各厂商截断/结束语义归一")
    void normalizes_finish_reasons() {
        assertEquals(FinishReason.LENGTH, FinishReason.from("length"));
        assertEquals(FinishReason.LENGTH, FinishReason.from("max_tokens"));
        assertEquals(FinishReason.STOP, FinishReason.from("stop"));
        assertEquals(FinishReason.STOP, FinishReason.from("end_turn"));
        assertEquals(FinishReason.TOOL_CALLS, FinishReason.from("tool_calls"));
        assertEquals(FinishReason.TOOL_CALLS, FinishReason.from("tool_use"));
        assertEquals(FinishReason.UNKNOWN, FinishReason.from(null));
    }
}

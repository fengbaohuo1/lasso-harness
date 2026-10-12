package com.jnf.harness.domain.llm.model.valobj;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 思考强度旋钮验收（P9-8）：off/low/medium/high 归一与非法值拒绝。
 */
class GenerateOptionsTest {

    private GenerateOptions with(String effort) {
        return GenerateOptions.builder().channel(LlmChannel.openai("http://x", "k"))
                .model("m").reasoningEffort(effort).build();
    }

    @Test
    @DisplayName("合法值归一小写；空串归 null")
    void normalize() {
        assertEquals("high", with("HIGH").reasoningEffort());
        assertEquals("low", with(" Low ").reasoningEffort());
        assertNull(with("").reasoningEffort());
        assertNull(with(null).reasoningEffort());
    }

    @Test
    @DisplayName("非法值拒绝")
    void reject() {
        assertThrows(IllegalArgumentException.class, () -> with("ultra"));
    }
}

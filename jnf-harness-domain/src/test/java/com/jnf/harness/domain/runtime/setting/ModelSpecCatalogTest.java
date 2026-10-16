package com.jnf.harness.domain.runtime.setting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型规格目录（P11-2 补丁）。
 * <p>重点锁住两件事：① 小米 MiMo 2.5 / 2.6 系的规格必须是 1M 上下文 / 128K 输出（早期被启发式
 * 兜底错猜成 128K / 8K）；② ASR / TTS 这类非对话模型不给假规格，而是标记为非对话模型。
 */
class ModelSpecCatalogTest {

    @Test
    @DisplayName("小米 MiMo 2.6 系取 1M 上下文 / 128K 输出")
    void mimo26UsesOfficialSpec() {
        ModelSpecCatalog.ModelSpec spec = ModelSpecCatalog.resolve("xiaomi", "mimo-v2.6-flash");
        assertTrue(spec.known(), "2.6 系应命中内置目录");
        assertTrue(spec.chatModel(), "2.6 系是对话模型");
        assertEquals(1_048_576, spec.contextWindow());
        assertEquals(131_072, spec.maxOutputTokens());
        assertTrue(spec.note().contains("2.6"), "备注应指明系列，便于人工核对：" + spec.note());
    }

    @Test
    @DisplayName("小米 MiMo 2.5 系按同代规格取 1M / 128K")
    void mimo25UsesFamilySpec() {
        for (String model : new String[]{"mimo-v2.5", "mimo-v2.5-pro"}) {
            ModelSpecCatalog.ModelSpec spec = ModelSpecCatalog.resolve("xiaomi", model);
            assertTrue(spec.known(), model + " 应命中内置目录");
            assertEquals(1_048_576, spec.contextWindow(), model);
            assertEquals(131_072, spec.maxOutputTokens(), model);
        }
    }

    @Test
    @DisplayName("非对话模型（ASR / TTS）不给假规格，标记为非对话")
    void nonChatModelsAreFlagged() {
        for (String model : new String[]{
                "mimo-v2.5-asr", "mimo-v2.5-tts", "mimo-v2.5-tts-voiceclone",
                "mimo-v2.5-tts-voicedesign", "text-embedding-3-small"}) {
            ModelSpecCatalog.ModelSpec spec = ModelSpecCatalog.resolve("xiaomi", model);
            assertFalse(spec.chatModel(), model + " 不应被当作对话模型");
            assertFalse(spec.known(), model + " 不应声称已知规格");
            assertTrue(spec.note().contains("非对话模型"), model + " 备注应说明原因：" + spec.note());
        }
    }

    @Test
    @DisplayName("已收录的对话模型规格与图片能力保持正确")
    void knownChatModels() {
        ModelSpecCatalog.ModelSpec gpt4o = ModelSpecCatalog.resolve("openai", "gpt-4o");
        assertTrue(gpt4o.known());
        assertEquals(128_000, gpt4o.contextWindow());
        assertEquals(16_384, gpt4o.maxOutputTokens());
        assertTrue(gpt4o.inputModalities().contains(ModelEntity.MODALITY_IMAGE));

        ModelSpecCatalog.ModelSpec r1 = ModelSpecCatalog.resolve("deepseek", "deepseek-reasoner");
        assertTrue(r1.known());
        assertEquals(32_768, r1.maxOutputTokens());
        assertFalse(r1.inputModalities().contains(ModelEntity.MODALITY_IMAGE));
    }

    @Test
    @DisplayName("未收录模型走启发式：取 -32k 标称并标为估算")
    void unknownModelFallsBackToGuess() {
        ModelSpecCatalog.ModelSpec spec = ModelSpecCatalog.resolve("custom", "some-unknown-32k");
        assertFalse(spec.known());
        assertTrue(spec.chatModel());
        assertEquals(32 * 1024, spec.contextWindow());
        assertEquals(ModelEntity.DEFAULT_MAX_OUTPUT_TOKENS, spec.maxOutputTokens());
    }
}

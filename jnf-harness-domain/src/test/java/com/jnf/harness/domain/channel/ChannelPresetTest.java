package com.jnf.harness.domain.channel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 渠道预设（厂商模板）。
 * <p>补丁：加入小米 MiMo，方便一键填空。
 */
class ChannelPresetTest {

    @Test
    @DisplayName("内置预设包含小米 MiMo，且地址与推荐模型正确")
    void containsXiaomiPreset() {
        List<ChannelPreset> presets = ChannelPreset.presets();
        Optional<ChannelPreset> xiaomi = presets.stream()
                .filter(preset -> "xiaomi".equals(preset.providerCode()))
                .findFirst();
        assertTrue(xiaomi.isPresent(), "预设里应有 providerCode=xiaomi 的条目");
        ChannelPreset preset = xiaomi.get();
        assertEquals("openai", preset.protocol(), "小米 MiMo 走 OpenAI 兼容协议");
        assertEquals("https://api.xiaomimimo.com/v1", preset.baseUrl());
        assertEquals("mimo-v2.6-flash", preset.recommendedModel());
    }

    @Test
    @DisplayName("预设的 providerCode 不重复")
    void providerCodesAreUnique() {
        List<ChannelPreset> presets = ChannelPreset.presets();
        long distinct = presets.stream().map(ChannelPreset::providerCode).distinct().count();
        assertEquals(presets.size(), distinct, "同一个 providerCode 不应出现两次");
    }
}

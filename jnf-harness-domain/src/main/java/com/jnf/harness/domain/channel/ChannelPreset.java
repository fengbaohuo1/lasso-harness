package com.jnf.harness.domain.channel;

import java.util.List;

/**
 * 渠道预设（厂商模板，前端快捷填充，不落库）。
 */
public record ChannelPreset(String providerCode, String displayName, String protocol,
                            String baseUrl, String recommendedModel) {

    /**
     * 内置模板。保存以数据库为准；模板只负责"填空"。
     */
    public static List<ChannelPreset> presets() {
        return List.of(
                new ChannelPreset("openai", "OpenAI", "openai", "https://api.openai.com/v1", "gpt-4o"),
                new ChannelPreset("deepseek", "DeepSeek", "openai", "https://api.deepseek.com/v1", "deepseek-chat"),
                new ChannelPreset("qwen", "通义千问", "openai", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-max"),
                new ChannelPreset("zhipu", "智谱 GLM", "openai", "https://open.bigmodel.cn/api/paas/v4", "glm-4-plus"),
                new ChannelPreset("anthropic", "Anthropic Claude", "anthropic", "https://api.anthropic.com", "claude-sonnet-4-5"),
                new ChannelPreset("ollama", "Ollama 本地", "ollama", "http://127.0.0.1:11434", "qwen2.5"),
                new ChannelPreset("custom", "自定义", "openai", "http://127.0.0.1:8777/v1", ""));
    }
}

package com.jnf.harness.domain.runtime.setting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 官方模型规格目录（P11-2）。
 * <p>用户要求「对于渠道自动选择的模型还是要可以自动填写人家官方支持的上下文长度和输出长度」——
 * 官方规格是有限集合且变化慢，放进代码里比放配置表更好维护、可单测。
 * <p>匹配顺序：{@link #resolve} 先按 {@code providerCode + modelCode 子串} 命中内置目录（{@code known=true}），
 * 未命中则走启发式兜底（{@code known=false}：从 {@code -128k} 这类标称解析上下文窗口，按词根猜图片输入能力）。
 * UI 只把 {@code known=true} 的值当作「官方规格」自动填入，其余标注为估算值，用户仍可手改。
 */
public final class ModelSpecCatalog {

    /** 规格条目：{@code pattern} 为 modelCode 的子串（小写比较），命中即采用。 */
    public record Entry(String providerCode, String pattern, int contextWindow, int maxOutputTokens,
                        boolean imageInput, String note) {
    }

    /** 解析结果。 */
    public record ModelSpec(String providerCode, String modelCode, int contextWindow, int maxOutputTokens,
                            List<String> inputModalities, List<String> outputModalities, boolean known, String note) {

        /** 落成模型实体（默认/启用的判定由调用方覆盖）。 */
        public ModelEntity toEntity(String channelCode, String displayName, boolean enabled, boolean isDefault,
                                   int sortOrder) {
            return new ModelEntity(channelCode, modelCode, displayName, contextWindow, maxOutputTokens,
                    inputModalities, outputModalities, enabled, isDefault, sortOrder);
        }
    }

    private static final List<String> TEXT_ONLY = List.of(ModelEntity.MODALITY_TEXT);
    private static final List<String> TEXT_AND_IMAGE =
            List.of(ModelEntity.MODALITY_TEXT, ModelEntity.MODALITY_IMAGE);
    private static final List<String> OUTPUT_TEXT_ONLY = List.of(ModelEntity.MODALITY_TEXT);

    private static final Pattern KILO_SUFFIX = Pattern.compile("-(\\d{1,4})k\\b");
    private static final List<String> VISION_ROOTS = List.of(
            "vl", "vision", "4o", "omni", "gpt-4-turbo", "gpt-4.1", "claude-3", "claude-4",
            "gemini", "glm-4v", "pixtral", "llava", "qwen-vl", "internvl", "minicpm-v");

    /** 内置目录：先具体后笼统（顺序敏感，首个命中的 pattern 生效）。 */
    private static final List<Entry> ENTRIES = List.of(
            // —— DeepSeek ——
            new Entry("deepseek", "deepseek-reasoner", 128_000, 32_768, false, "DeepSeek 推理模型（R1/V3.1 系）"),
            new Entry("deepseek", "deepseek-r1", 128_000, 32_768, false, "DeepSeek R1"),
            new Entry("deepseek", "deepseek-vl", 128_000, 8_192, true, "DeepSeek 视觉模型"),
            new Entry("deepseek", "deepseek-chat", 128_000, 8_192, false, "DeepSeek 对话模型（V3 系）"),
            new Entry("deepseek", "deepseek", 128_000, 8_192, false, "DeepSeek 通用"),
            // —— OpenAI ——
            new Entry("openai", "gpt-4.1", 1_047_576, 32_768, true, "GPT-4.1 系（1M 上下文）"),
            new Entry("openai", "gpt-4o", 128_000, 16_384, true, "GPT-4o 系"),
            new Entry("openai", "gpt-4-turbo", 128_000, 4_096, true, "GPT-4 Turbo"),
            new Entry("openai", "gpt-3.5", 16_385, 4_096, false, "GPT-3.5 Turbo"),
            new Entry("openai", "o1", 200_000, 100_000, false, "OpenAI o1 推理模型"),
            new Entry("openai", "o3", 200_000, 100_000, false, "OpenAI o3 推理模型"),
            new Entry("openai", "o4", 200_000, 100_000, false, "OpenAI o4 推理模型"),
            new Entry("openai", "gpt-4", 8_192, 4_096, false, "GPT-4"),
            // —— Anthropic ——
            new Entry("anthropic", "claude-sonnet-4", 200_000, 64_000, true, "Claude Sonnet 4"),
            new Entry("anthropic", "claude-opus-4", 200_000, 32_000, true, "Claude Opus 4"),
            new Entry("anthropic", "claude-3-7", 200_000, 64_000, true, "Claude 3.7 Sonnet"),
            new Entry("anthropic", "claude-3-5", 200_000, 8_192, true, "Claude 3.5 系"),
            new Entry("anthropic", "claude-3", 200_000, 4_096, true, "Claude 3 系"),
            new Entry("anthropic", "claude", 200_000, 8_192, true, "Claude 通用"),
            // —— Google Gemini ——
            new Entry("google", "gemini-1.5-pro", 2_097_152, 8_192, true, "Gemini 1.5 Pro（2M 上下文）"),
            new Entry("google", "gemini-2", 1_048_576, 8_192, true, "Gemini 2 系"),
            new Entry("google", "gemini", 1_048_576, 8_192, true, "Gemini 通用"),
            // —— 通义千问 ——
            new Entry("qwen", "qwen-vl", 128_000, 8_192, true, "Qwen-VL 视觉模型"),
            new Entry("qwen", "qwen3", 128_000, 32_768, false, "Qwen3"),
            new Entry("qwen", "qwen2.5", 128_000, 8_192, false, "Qwen2.5"),
            new Entry("qwen", "qwen", 128_000, 8_192, false, "Qwen 通用"),
            // —— 智谱 GLM ——
            new Entry("zhipu", "glm-4v", 128_000, 4_096, true, "GLM-4V 视觉模型"),
            new Entry("zhipu", "glm-4", 128_000, 4_096, false, "GLM-4"),
            // —— 月之暗面 Kimi ——
            new Entry("moonshot", "kimi-k2", 128_000, 16_384, false, "Kimi K2"),
            new Entry("moonshot", "moonshot", 128_000, 16_384, false, "Moonshot 通用"),
            // —— Ollama 本地常见模型 ——
            new Entry("ollama", "llama3", 128_000, 8_192, false, "Llama 3"),
            new Entry("ollama", "mistral", 32_768, 8_192, false, "Mistral"),
            new Entry("ollama", "llava", 32_768, 4_096, true, "LLaVA 视觉模型"));

    private ModelSpecCatalog() {
    }

    /** 全部内置条目（只读，供测试与排查）。 */
    public static List<Entry> entries() {
        return ENTRIES;
    }

    /**
     * 解析模型规格：先查内置目录（含 providerCode 限定与通配），未命中走启发式。
     * <p>providerCode 为空时仍会尝试全局子串匹配——很多渠道的 providerCode 是用户自填的（如 {@code xiaomi}），
     * 此时模型名本身（如 {@code gpt-4o}）仍能给出正确规格。
     */
    public static ModelSpec resolve(String providerCode, String modelCode) {
        String provider = normalize(providerCode);
        String model = modelCode == null ? "" : modelCode.trim();
        String lower = model.toLowerCase(Locale.ROOT);
        Entry fallbackEntry = null;
        for (Entry entry : ENTRIES) {
            if (!lower.contains(entry.pattern())) {
                continue;
            }
            if (entry.providerCode().equals(provider)) {
                return toSpec(providerCode, model, entry);
            }
            if (fallbackEntry == null) {
                fallbackEntry = entry;
            }
        }
        if (fallbackEntry != null) {
            return toSpec(providerCode, model, fallbackEntry);
        }
        return guess(providerCode, model);
    }

    /** 启发式兜底：从 {@code -128k} 标称取上下文窗口，按词根猜图片能力，输出一律文本。 */
    public static ModelSpec guess(String providerCode, String modelCode) {
        String model = modelCode == null ? "" : modelCode.trim();
        String lower = model.toLowerCase(Locale.ROOT);
        Matcher matcher = KILO_SUFFIX.matcher(lower);
        int contextWindow = ModelEntity.DEFAULT_CONTEXT_WINDOW;
        if (matcher.find()) {
            contextWindow = Integer.parseInt(matcher.group(1)) * 1024;
        }
        boolean image = false;
        for (String root : VISION_ROOTS) {
            if (lower.contains(root)) {
                image = true;
                break;
            }
        }
        return new ModelSpec(providerCode, model, contextWindow, ModelEntity.DEFAULT_MAX_OUTPUT_TOKENS,
                image ? TEXT_AND_IMAGE : TEXT_ONLY, OUTPUT_TEXT_ONLY, false,
                image ? "未收录：按名称推测支持图片输入，规格为估算值" : "未收录：规格为估算值，可手动修正");
    }

    private static ModelSpec toSpec(String providerCode, String modelCode, Entry entry) {
        List<String> modalities = new ArrayList<>(entry.imageInput() ? TEXT_AND_IMAGE : TEXT_ONLY);
        return new ModelSpec(providerCode, modelCode, entry.contextWindow(), entry.maxOutputTokens(),
                modalities, OUTPUT_TEXT_ONLY, true, entry.note());
    }

    private static String normalize(String providerCode) {
        return providerCode == null ? "" : providerCode.trim().toLowerCase(Locale.ROOT);
    }
}

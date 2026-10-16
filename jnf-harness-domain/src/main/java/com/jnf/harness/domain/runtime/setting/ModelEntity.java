package com.jnf.harness.domain.runtime.setting;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 模型条目（P11-1）：归属于某个渠道，承载「能力与规格」。
 * <p>与 {@link ModelSettingEntity}（渠道 = 连接 + 密钥 + 默认模型）分层：一个渠道可挂多个模型，
 * 模型才有上下文窗口、最大输出、输入/输出模态。P11-2 的官方规格也落在这些字段上。
 * <p>模型引用（P11-6）统一为 {@code channelCode::modelCode}（{@link #ref()}）。
 */
public record ModelEntity(
        String channelCode,
        String modelCode,
        String displayName,
        Integer contextWindow,
        Integer maxOutputTokens,
        List<String> inputModalities,
        List<String> outputModalities,
        boolean enabled,
        boolean isDefault,
        int sortOrder) {

    /** 输入模态：文本。 */
    public static final String MODALITY_TEXT = "text";
    /** 输入模态：图片（P11-4 多模态输入）。 */
    public static final String MODALITY_IMAGE = "image";
    /** 输入模态白名单：P11 起至少支持文本与图片。 */
    public static final Set<String> INPUT_MODALITY_WHITELIST = Set.of(MODALITY_TEXT, MODALITY_IMAGE);
    /** 输出模态白名单：当前只支持文本（用户明确要求）。 */
    public static final Set<String> OUTPUT_MODALITY_WHITELIST = Set.of(MODALITY_TEXT);
    /** 未知规格时的兜底上下文窗口。 */
    public static final int DEFAULT_CONTEXT_WINDOW = 128_000;
    /** 未知规格时的兜底最大输出。 */
    public static final int DEFAULT_MAX_OUTPUT_TOKENS = 8_192;
    /** 模型引用分隔符。 */
    public static final String REF_SEPARATOR = "::";
    /** 模态落库分隔符。 */
    private static final String MODALITY_STORAGE_SEPARATOR = ",";

    public ModelEntity {
        if (channelCode == null || channelCode.isBlank()) {
            throw new IllegalArgumentException("channelCode 必填");
        }
        if (modelCode == null || modelCode.isBlank()) {
            throw new IllegalArgumentException("modelCode 必填");
        }
        channelCode = channelCode.trim();
        modelCode = modelCode.trim();
        displayName = displayName == null || displayName.isBlank() ? modelCode : displayName.trim();
        inputModalities = normalize(inputModalities, INPUT_MODALITY_WHITELIST, "inputModalities");
        outputModalities = normalize(outputModalities, OUTPUT_MODALITY_WHITELIST, "outputModalities");
        if (contextWindow != null && contextWindow <= 0) {
            throw new IllegalArgumentException("contextWindow 必须为正整数");
        }
        if (maxOutputTokens != null && maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens 必须为正整数");
        }
        if (sortOrder < 0) {
            sortOrder = 0;
        }
    }

    /** 最小可用的模型条目（只有编码；规格留空走兜底）。 */
    public static ModelEntity minimal(String channelCode, String modelCode) {
        return new ModelEntity(channelCode, modelCode, modelCode, null, null,
                List.of(MODALITY_TEXT), List.of(MODALITY_TEXT), true, false, 0);
    }

    /** 模型引用 {@code channelCode::modelCode}。 */
    public String ref() {
        return channelCode + REF_SEPARATOR + modelCode;
    }

    /** 是否支持图片输入（P11-4 入模前置判断）。 */
    public boolean supportsImageInput() {
        return inputModalities.contains(MODALITY_IMAGE);
    }

    /** 生效上下文窗口（未配置走兜底）。 */
    public int effectiveContextWindow() {
        return contextWindow == null || contextWindow <= 0 ? DEFAULT_CONTEXT_WINDOW : contextWindow;
    }

    /** 生效最大输出（未配置走兜底）。 */
    public int effectiveMaxOutputTokens() {
        return maxOutputTokens == null || maxOutputTokens <= 0 ? DEFAULT_MAX_OUTPUT_TOKENS : maxOutputTokens;
    }

    /**
     * 解析模型引用：{@code channelCode::modelCode} 返回 {@code [channelCode, modelCode]}；
     * 不含分隔符（老数据的裸 modelCode）返回 {@code null}，由调用方按 modelCode 全表匹配。
     */
    public static String[] parseRef(String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        int index = ref.indexOf(REF_SEPARATOR);
        if (index <= 0 || index >= ref.length() - REF_SEPARATOR.length()) {
            return null;
        }
        String channel = ref.substring(0, index).trim();
        String model = ref.substring(index + REF_SEPARATOR.length()).trim();
        if (channel.isEmpty() || model.isEmpty()) {
            return null;
        }
        return new String[] {channel, model};
    }

    /** 模态列表 → 落库字符串（{@code text,image}）。 */
    public static String encodeModalities(List<String> modalities) {
        return String.join(MODALITY_STORAGE_SEPARATOR, normalize(modalities, INPUT_MODALITY_WHITELIST, "modalities"));
    }

    /** 落库字符串 → 模态列表；空值回落 {@code [text]}。 */
    public static List<String> decodeModalities(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of(MODALITY_TEXT);
        }
        return normalize(List.of(raw.split(MODALITY_STORAGE_SEPARATOR)), INPUT_MODALITY_WHITELIST, "modalities");
    }

    private static List<String> normalize(List<String> modalities, Set<String> whitelist, String field) {
        if (modalities == null || modalities.isEmpty()) {
            return List.of(MODALITY_TEXT);
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String modality : modalities) {
            if (modality == null || modality.isBlank()) {
                continue;
            }
            String value = modality.trim().toLowerCase();
            if (!whitelist.contains(value)) {
                throw new IllegalArgumentException(field + " 只能是 " + whitelist + "，实际: " + modality);
            }
            distinct.add(value);
        }
        return distinct.isEmpty() ? List.of(MODALITY_TEXT) : new ArrayList<>(distinct);
    }
}

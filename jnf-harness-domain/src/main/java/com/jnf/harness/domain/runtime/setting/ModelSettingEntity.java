package com.jnf.harness.domain.runtime.setting;

/**
 * 模型渠道实体（一行一渠道；api_key 只存引用，不落明文 —— R9）。
 */
public record ModelSettingEntity(String channelCode, String displayName, String providerCode,
                                 String modelCode, String baseUrl, String apiKeyRef,
                                 String protocol, boolean enabled, boolean active) {
}

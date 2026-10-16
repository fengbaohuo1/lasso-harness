package com.jnf.harness.domain.runtime.setting;

import com.jnf.harness.domain.runtime.secret.SecretRefResolver;

import java.util.List;
import java.util.Optional;

/**
 * 模型渠道服务：保存/删除/激活/解析当前生效渠道。
 * <p>M1：支持写入式 {@code apiKey}（存密钥库，只进不出，R9）；M2：激活/删除保护校验。
 */
public class ModelSettingService {

    private final IModelSettingRepository repository;
    private final SecretRefResolver secrets;

    public ModelSettingService(IModelSettingRepository repository, SecretRefResolver secrets) {
        this.repository = repository;
        this.secrets = secrets;
    }

    /** 保存（upsert）并校验协议；沿用既有 apiKeyRef。 */
    public ModelSettingEntity save(ModelSettingEntity entity) {
        return save(entity, null);
    }

    /**
     * 保存（upsert）。
     * <p>P11-3 密钥三分语义：{@code apiKey == null} → 不修改既有密钥；{@code apiKey} 为空串 →
     * 清除密钥（{@code apiKeyRef} 置空）；非空 → 写入密钥库并回填 {@code secret:apiKey.<channelCode>}（值不落库）。
     */
    public ModelSettingEntity save(ModelSettingEntity entity, String apiKey) {
        if (entity.channelCode() == null || entity.channelCode().isBlank()) {
            throw new IllegalArgumentException("channelCode 必填");
        }
        if (entity.baseUrl() == null || entity.baseUrl().isBlank()) {
            throw new IllegalArgumentException("baseUrl 必填");
        }
        String protocol = entity.protocol() == null || entity.protocol().isBlank()
                ? "openai" : entity.protocol().trim().toLowerCase();
        if (!protocol.matches("openai|anthropic|ollama")) {
            throw new IllegalArgumentException("protocol 只能是 openai/anthropic/ollama");
        }
        String channelCode = entity.channelCode().trim();
        String apiKeyRef = entity.apiKeyRef();
        if (apiKey != null) {
            if (apiKey.isBlank()) {
                secrets.deleteSecret("apiKey." + channelCode);
                apiKeyRef = null;
            } else {
                apiKeyRef = secrets.putSecret("apiKey." + channelCode, apiKey.trim());
            }
        }
        ModelSettingEntity normalized = new ModelSettingEntity(
                channelCode, entity.displayName(), entity.providerCode(),
                entity.modelCode(), entity.baseUrl().trim(), apiKeyRef,
                protocol, entity.enabled(), false);
        repository.upsert(normalized);
        return normalized;
    }

    /** 删除；当前生效渠道禁止删除（先切换再删，M2 保护）。 */
    public void delete(String channelCode) {
        if (repository.findActive().map(active -> active.channelCode().equals(channelCode)).orElse(false)) {
            throw new IllegalArgumentException("该渠道为当前生效渠道，请先启用其他渠道再删除: " + channelCode);
        }
        repository.delete(channelCode);
    }

    public List<ModelSettingEntity> list() {
        return repository.findAll();
    }

    /** 激活（唯一生效）；不存在或已禁用的渠道拒绝激活（M2）。 */
    public void activate(String channelCode) {
        ModelSettingEntity entity = repository.findAll().stream()
                .filter(item -> item.channelCode().equals(channelCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("渠道不存在: " + channelCode));
        if (!entity.enabled()) {
            throw new IllegalArgumentException("渠道已禁用，不能启用: " + channelCode);
        }
        repository.activate(channelCode);
    }

    /** 当前生效渠道（无则空）。 */
    public Optional<ModelSettingEntity> active() {
        return repository.findActive();
    }

    /** 渠道密钥是否已配置（只回布尔，不回值，R9）。 */
    public boolean hasApiKey(String channelCode) {
        return repository.findAll().stream()
                .filter(item -> item.channelCode().equals(channelCode))
                .findFirst()
                .map(item -> secrets.hasSecret(item.apiKeyRef()))
                .orElse(false);
    }

    /** 解析渠道密钥（内部联调用；不出 REST 响应）。 */
    public String resolveApiKey(String channelCode) {
        return repository.findAll().stream()
                .filter(item -> item.channelCode().equals(channelCode))
                .findFirst()
                .map(item -> secrets.resolve(item.apiKeyRef()))
                .orElse("");
    }

    public SecretRefResolver secrets() {
        return secrets;
    }
}

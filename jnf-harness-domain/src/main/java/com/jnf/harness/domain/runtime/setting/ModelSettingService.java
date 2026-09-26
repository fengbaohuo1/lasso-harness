package com.jnf.harness.domain.runtime.setting;

import java.util.List;
import java.util.Optional;

/**
 * 模型渠道服务：保存/删除/激活/解析当前生效渠道。
 */
public class ModelSettingService {

    private final IModelSettingRepository repository;

    public ModelSettingService(IModelSettingRepository repository) {
        this.repository = repository;
    }

    /** 保存（upsert）并校验协议。 */
    public ModelSettingEntity save(ModelSettingEntity entity) {
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
        ModelSettingEntity normalized = new ModelSettingEntity(
                entity.channelCode().trim(), entity.displayName(), entity.providerCode(),
                entity.modelCode(), entity.baseUrl().trim(), entity.apiKeyRef(),
                protocol, entity.enabled(), false);
        repository.upsert(normalized);
        return normalized;
    }

    public void delete(String channelCode) {
        repository.delete(channelCode);
    }

    public List<ModelSettingEntity> list() {
        return repository.findAll();
    }

    public void activate(String channelCode) {
        repository.activate(channelCode);
    }

    /** 当前生效渠道（无则空）。 */
    public Optional<ModelSettingEntity> active() {
        return repository.findActive();
    }
}

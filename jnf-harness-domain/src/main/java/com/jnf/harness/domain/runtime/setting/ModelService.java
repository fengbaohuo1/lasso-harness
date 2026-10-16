package com.jnf.harness.domain.runtime.setting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 模型条目服务（P11-1）：渠道下的多模型管理。
 * <p>不变式：
 * <ul>
 *   <li>渠道表的 {@code model_code} 始终等于该渠道的默认模型编码（{@code harness_model.is_default}）；</li>
 *   <li>同一渠道最多一个默认模型；</li>
 *   <li>删除默认模型时自动顺延到剩余模型，没有剩余则把渠道默认置空。</li>
 * </ul>
 * 这些不变式让 P11-6 的模型解析（{@code channelCode::modelCode}）与老数据兼容（裸 modelCode）能同时成立。
 */
public class ModelService {

    private final IModelRepository repository;
    private final IModelSettingRepository channelRepository;

    public ModelService(IModelRepository repository, IModelSettingRepository channelRepository) {
        this.repository = repository;
        this.channelRepository = channelRepository;
    }

    /** 全量模型（渠道 → 排序 → 编码）。 */
    public List<ModelEntity> list() {
        List<ModelEntity> models = new ArrayList<>(repository.findAll());
        models.sort(Comparator.comparing(ModelEntity::channelCode)
                .thenComparingInt(ModelEntity::sortOrder)
                .thenComparing(ModelEntity::modelCode));
        return models;
    }

    /** 指定渠道下的模型。 */
    public List<ModelEntity> listByChannel(String channelCode) {
        return list().stream().filter(model -> model.channelCode().equals(channelCode)).toList();
    }

    /** 可选用模型清单（仅启用）：供对话框模型选择器按渠道分组。 */
    public List<ModelEntity> catalog() {
        Set<String> disabledChannels = new LinkedHashSet<>();
        for (ModelSettingEntity channel : channelRepository.findAll()) {
            if (!channel.enabled()) {
                disabledChannels.add(channel.channelCode());
            }
        }
        return list().stream()
                .filter(ModelEntity::enabled)
                .filter(model -> !disabledChannels.contains(model.channelCode()))
                .toList();
    }

    public Optional<ModelEntity> find(String channelCode, String modelCode) {
        if (channelCode == null || modelCode == null) {
            return Optional.empty();
        }
        return repository.findByChannel(channelCode).stream()
                .filter(model -> model.modelCode().equals(modelCode))
                .findFirst();
    }

    /**
     * 解析模型引用（P11-6）：
     * <ol>
     *   <li>{@code channelCode::modelCode} → 精确匹配该渠道下的模型；</li>
     *   <li>裸 {@code modelCode}（老会话数据）→ 全表按编码匹配（优先启用）；</li>
     *   <li>都命中不了 → 空，由调用方回落到生效渠道。</li>
     * </ol>
     */
    public Optional<ModelEntity> resolve(String modelRef) {
        if (modelRef == null || modelRef.isBlank()) {
            return Optional.empty();
        }
        String trimmed = modelRef.trim();
        String[] parts = ModelEntity.parseRef(trimmed);
        if (parts != null) {
            return find(parts[0], parts[1]);
        }
        List<ModelEntity> matched = list().stream()
                .filter(model -> model.modelCode().equals(trimmed))
                .toList();
        return matched.stream().filter(ModelEntity::enabled).findFirst()
                .or(() -> matched.stream().findFirst());
    }

    /** 保存单个模型；置为默认时同步渠道默认模型。 */
    public ModelEntity save(ModelEntity entity) {
        repository.upsert(entity);
        if (entity.isDefault()) {
            applyDefault(entity.channelCode(), entity.modelCode());
        }
        return entity;
    }

    /**
     * 整体同步某渠道的模型集合（P11-8 渠道设置页保存路径）：
     * 删除已移除的行、upsert 提交的行、保证唯一默认并回写渠道表的 {@code model_code}。
     */
    public List<ModelEntity> sync(String channelCode, List<ModelEntity> models, String defaultModelCode) {
        if (channelCode == null || channelCode.isBlank()) {
            throw new IllegalArgumentException("channelCode 必填");
        }
        List<ModelEntity> incoming = new ArrayList<>();
        if (models != null) {
            for (ModelEntity model : models) {
                if (model == null) {
                    continue;
                }
                int order = incoming.size();
                incoming.add(new ModelEntity(channelCode, model.modelCode(), model.displayName(),
                        model.contextWindow(), model.maxOutputTokens(), model.inputModalities(),
                        model.outputModalities(), model.enabled(), false, order));
            }
        }
        Set<String> incomingCodes = new LinkedHashSet<>();
        for (ModelEntity model : incoming) {
            incomingCodes.add(model.modelCode());
        }
        for (ModelEntity existing : repository.findByChannel(channelCode)) {
            if (!incomingCodes.contains(existing.modelCode())) {
                repository.delete(channelCode, existing.modelCode());
            }
        }
        for (ModelEntity model : incoming) {
            repository.upsert(model);
        }
        String target = pickDefault(incoming, defaultModelCode);
        if (target == null) {
            applyDefault(channelCode, null);
        } else {
            applyDefault(channelCode, target);
        }
        return listByChannel(channelCode);
    }

    /**
     * 删除模型；若删的是渠道默认模型则顺延（无剩余则置空渠道默认）。
     *
     * @return 是否删除了行
     */
    public boolean delete(String channelCode, String modelCode) {
        if (find(channelCode, modelCode).isEmpty()) {
            return false;
        }
        repository.delete(channelCode, modelCode);
        if (modelCode.equals(channelDefault(channelCode))) {
            String next = pickDefault(repository.findByChannel(channelCode), null);
            applyDefault(channelCode, next);
        }
        return true;
    }

    /** 渠道删除时级联清空模型行（先于渠道行删除调用）。 */
    public void deleteByChannel(String channelCode) {
        repository.deleteByChannel(channelCode);
    }

    /** 渠道默认模型编码（渠道表 {@code model_code}；未设置返回 null）。 */
    public String channelDefault(String channelCode) {
        return channelRepository.findAll().stream()
                .filter(channel -> channel.channelCode().equals(channelCode))
                .map(ModelSettingEntity::modelCode)
                .filter(code -> code != null && !code.isBlank())
                .findFirst()
                .orElse(null);
    }

    /** 把渠道默认模型写回渠道行（并保证模型表里唯一 default）；modelCode 为空则只清空。 */
    private void applyDefault(String channelCode, String modelCode) {
        repository.clearDefault(channelCode);
        if (modelCode != null && !modelCode.isBlank()) {
            repository.markDefault(channelCode, modelCode);
        }
        writeChannelDefault(channelCode, modelCode);
    }

    private void writeChannelDefault(String channelCode, String modelCode) {
        channelRepository.findAll().stream()
                .filter(channel -> channel.channelCode().equals(channelCode))
                .findFirst()
                .ifPresent(channel -> channelRepository.upsert(new ModelSettingEntity(
                        channel.channelCode(), channel.displayName(), channel.providerCode(), modelCode,
                        channel.baseUrl(), channel.apiKeyRef(), channel.protocol(),
                        channel.enabled(), channel.active())));
    }

    /** 选定默认模型：显式指定 → 首个启用 → 首个；空集合返回 null。 */
    private static String pickDefault(List<ModelEntity> models, String preferred) {
        if (models.isEmpty()) {
            return null;
        }
        if (preferred != null && !preferred.isBlank()) {
            boolean hit = models.stream().anyMatch(model -> model.modelCode().equals(preferred));
            if (hit) {
                return preferred;
            }
        }
        return models.stream().filter(ModelEntity::enabled).findFirst()
                .or(() -> models.stream().findFirst())
                .map(ModelEntity::modelCode)
                .orElse(null);
    }
}

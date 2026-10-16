package com.jnf.harness.app.boot;

import com.jnf.harness.domain.runtime.setting.ModelEntity;
import com.jnf.harness.domain.runtime.setting.ModelService;
import com.jnf.harness.domain.runtime.setting.ModelSettingEntity;
import com.jnf.harness.domain.runtime.setting.ModelSettingService;
import com.jnf.harness.domain.runtime.setting.ModelSpecCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.List;

/**
 * M2：首启 seed——渠道表为空且文件配置存在时，把 harness.llm.default.* 落成 default 渠道，
 * 让桌面端首屏可见、可编辑（此后以 DB 为准，文件配置兜底）。
 * <p>P11-1：同时按官方规格建出该渠道的模型行，保证新库首启后对话框模型选择器就有内容。
 */
public class ModelSettingBootSeed implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ModelSettingBootSeed.class);

    private final ModelSettingService service;
    private final ModelService models;
    private final String defaultBaseUrl;
    private final String defaultApiKey;
    private final String defaultModel;
    private final boolean enabled;

    public ModelSettingBootSeed(ModelSettingService service, ModelService models, String defaultBaseUrl,
                               String defaultApiKey, String defaultModel, boolean enabled) {
        this.service = service;
        this.models = models;
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultApiKey = defaultApiKey;
        this.defaultModel = defaultModel;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled || !service.list().isEmpty() || defaultBaseUrl == null || defaultBaseUrl.isBlank()) {
            return;
        }
        ModelSettingEntity entity = new ModelSettingEntity(
                "default", "默认渠道（文件配置）", "custom",
                defaultModel == null ? "" : defaultModel,
                defaultBaseUrl, "", "openai", true, false);
        service.save(entity, (defaultApiKey == null || defaultApiKey.isBlank()) ? null : defaultApiKey);
        service.activate("default");
        if (defaultModel != null && !defaultModel.isBlank()) {
            ModelSpecCatalog.ModelSpec spec = ModelSpecCatalog.resolve("custom", defaultModel);
            models.sync("default", List.of(spec.toEntity("default", defaultModel, true, false, 0)), defaultModel);
        }
        log.info("已从文件配置 seed 模型渠道 default: {}", defaultBaseUrl);
    }
}

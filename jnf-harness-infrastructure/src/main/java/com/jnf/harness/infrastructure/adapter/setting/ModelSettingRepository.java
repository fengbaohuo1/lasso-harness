package com.jnf.harness.infrastructure.adapter.setting;

import com.jnf.harness.domain.runtime.setting.IModelSettingRepository;
import com.jnf.harness.domain.runtime.setting.ModelSettingEntity;
import com.jnf.harness.infrastructure.dao.ModelSettingDao;
import com.jnf.harness.infrastructure.dao.po.ModelSettingPO;

import java.util.List;
import java.util.Optional;

/**
 * 模型渠道仓储（MyBatis 实现）。
 */
public class ModelSettingRepository implements IModelSettingRepository {

    private final ModelSettingDao dao;

    public ModelSettingRepository(ModelSettingDao dao) {
        this.dao = dao;
    }

    @Override
    public void upsert(ModelSettingEntity entity) {
        dao.upsert(toPo(entity));
    }

    @Override
    public void delete(String channelCode) {
        dao.deleteByCode(channelCode);
    }

    @Override
    public List<ModelSettingEntity> findAll() {
        return dao.findAll().stream().map(ModelSettingRepository::toEntity).toList();
    }

    @Override
    public Optional<ModelSettingEntity> findActive() {
        return Optional.ofNullable(dao.findActive()).map(ModelSettingRepository::toEntity);
    }

    @Override
    public void activate(String channelCode) {
        dao.clearActive();
        dao.setActive(channelCode);
    }

    private static ModelSettingPO toPo(ModelSettingEntity entity) {
        ModelSettingPO po = new ModelSettingPO();
        po.setChannelCode(entity.channelCode());
        po.setDisplayName(entity.displayName());
        po.setProviderCode(entity.providerCode());
        po.setModelCode(entity.modelCode());
        po.setBaseUrl(entity.baseUrl());
        po.setApiKeyRef(entity.apiKeyRef());
        po.setProtocol(entity.protocol());
        po.setEnabled(entity.enabled());
        return po;
    }

    private static ModelSettingEntity toEntity(ModelSettingPO po) {
        return new ModelSettingEntity(po.getChannelCode(), po.getDisplayName(), po.getProviderCode(),
                po.getModelCode(), po.getBaseUrl(), po.getApiKeyRef(),
                po.getProtocol(), po.isEnabled(), po.isActive());
    }
}

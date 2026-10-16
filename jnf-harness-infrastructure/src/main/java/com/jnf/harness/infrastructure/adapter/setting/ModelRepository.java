package com.jnf.harness.infrastructure.adapter.setting;

import com.jnf.harness.domain.runtime.setting.IModelRepository;
import com.jnf.harness.domain.runtime.setting.ModelEntity;
import com.jnf.harness.infrastructure.dao.ModelDao;
import com.jnf.harness.infrastructure.dao.po.ModelPO;

import java.util.List;

/**
 * 模型条目仓储（P11-1，MyBatis 实现）。
 */
public class ModelRepository implements IModelRepository {

    private final ModelDao dao;

    public ModelRepository(ModelDao dao) {
        this.dao = dao;
    }

    @Override
    public void upsert(ModelEntity entity) {
        dao.upsert(toPo(entity));
    }

    @Override
    public void delete(String channelCode, String modelCode) {
        dao.deleteOne(channelCode, modelCode);
    }

    @Override
    public void deleteByChannel(String channelCode) {
        dao.deleteByChannel(channelCode);
    }

    @Override
    public List<ModelEntity> findAll() {
        return dao.findAll().stream().map(ModelRepository::toEntity).toList();
    }

    @Override
    public List<ModelEntity> findByChannel(String channelCode) {
        return dao.findByChannel(channelCode).stream().map(ModelRepository::toEntity).toList();
    }

    @Override
    public void clearDefault(String channelCode) {
        dao.clearDefault(channelCode);
    }

    @Override
    public void markDefault(String channelCode, String modelCode) {
        dao.markDefault(channelCode, modelCode);
    }

    private static ModelPO toPo(ModelEntity entity) {
        ModelPO po = new ModelPO();
        po.setChannelCode(entity.channelCode());
        po.setModelCode(entity.modelCode());
        po.setDisplayName(entity.displayName());
        po.setContextWindow(entity.contextWindow());
        po.setMaxOutputTokens(entity.maxOutputTokens());
        po.setInputModalities(ModelEntity.encodeModalities(entity.inputModalities()));
        po.setOutputModalities(ModelEntity.encodeModalities(entity.outputModalities()));
        po.setEnabled(entity.enabled());
        po.setDefaultFlag(entity.isDefault());
        po.setSortOrder(entity.sortOrder());
        return po;
    }

    private static ModelEntity toEntity(ModelPO po) {
        return new ModelEntity(po.getChannelCode(), po.getModelCode(), po.getDisplayName(),
                po.getContextWindow(), po.getMaxOutputTokens(),
                ModelEntity.decodeModalities(po.getInputModalities()),
                ModelEntity.decodeModalities(po.getOutputModalities()),
                po.isEnabled(), po.isDefaultFlag(), po.getSortOrder());
    }
}

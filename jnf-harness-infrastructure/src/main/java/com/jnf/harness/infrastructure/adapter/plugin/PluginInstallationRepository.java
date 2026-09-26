package com.jnf.harness.infrastructure.adapter.plugin;

import com.jnf.harness.domain.plugin.registry.IPluginInstallationRepository;
import com.jnf.harness.domain.plugin.registry.PluginInstallationEntity;
import com.jnf.harness.infrastructure.dao.PluginInstallationDao;
import com.jnf.harness.infrastructure.dao.po.PluginInstallationPO;

import java.util.List;
import java.util.Optional;

/**
 * 插件安装仓储（MyBatis 实现）。
 */
public class PluginInstallationRepository implements IPluginInstallationRepository {

    private final PluginInstallationDao dao;

    public PluginInstallationRepository(PluginInstallationDao dao) {
        this.dao = dao;
    }

    @Override
    public void upsert(PluginInstallationEntity entity) {
        dao.upsert(toPo(entity));
    }

    @Override
    public void updateStatus(String pluginId, String status) {
        dao.updateStatus(pluginId, status);
    }

    @Override
    public void delete(String pluginId) {
        dao.deleteByCode(pluginId);
    }

    @Override
    public List<PluginInstallationEntity> findAll() {
        return dao.findAll().stream().map(PluginInstallationRepository::toEntity).toList();
    }

    @Override
    public Optional<PluginInstallationEntity> findById(String pluginId) {
        return Optional.ofNullable(dao.findByCode(pluginId)).map(PluginInstallationRepository::toEntity);
    }

    private static PluginInstallationPO toPo(PluginInstallationEntity entity) {
        PluginInstallationPO po = new PluginInstallationPO();
        po.setPluginId(entity.pluginId());
        po.setDisplayName(entity.displayName());
        po.setPluginVersion(entity.pluginVersion());
        po.setRuntimeType(entity.runtimeType());
        po.setSourcePath(entity.sourcePath());
        po.setEntrypoint(entity.entrypoint());
        po.setStatus(entity.status());
        return po;
    }

    private static PluginInstallationEntity toEntity(PluginInstallationPO po) {
        return new PluginInstallationEntity(po.getPluginId(), po.getDisplayName(), po.getPluginVersion(),
                po.getRuntimeType(), po.getSourcePath(), po.getEntrypoint(), po.getStatus());
    }
}

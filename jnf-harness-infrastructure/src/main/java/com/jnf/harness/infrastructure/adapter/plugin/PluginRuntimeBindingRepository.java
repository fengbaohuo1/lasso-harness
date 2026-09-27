package com.jnf.harness.infrastructure.adapter.plugin;

import com.jnf.harness.domain.plugin.registry.IPluginRuntimeBindingRepository;
import com.jnf.harness.domain.plugin.registry.PluginRuntimeBindingEntity;
import com.jnf.harness.infrastructure.dao.PluginRuntimeBindingDao;
import com.jnf.harness.infrastructure.dao.po.PluginRuntimeBindingPO;

/**
 * 插件运行绑定仓储（MyBatis 实现）。
 */
public class PluginRuntimeBindingRepository implements IPluginRuntimeBindingRepository {

    private final PluginRuntimeBindingDao dao;

    public PluginRuntimeBindingRepository(PluginRuntimeBindingDao dao) {
        this.dao = dao;
    }

    @Override
    public void upsert(PluginRuntimeBindingEntity entity) {
        PluginRuntimeBindingPO po = new PluginRuntimeBindingPO();
        po.setPluginId(entity.pluginId());
        po.setPackageType(entity.packageType());
        po.setManifestPath(entity.manifestPath());
        po.setWorkingDirectory(entity.workingDirectory());
        po.setRuntimeCommand(entity.runtimeCommand());
        po.setBridgeReady(entity.bridgeReady());
        po.setMessage(entity.message());
        dao.upsert(po);
    }
}

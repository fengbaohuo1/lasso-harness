package com.jnf.harness.infrastructure.adapter.plugin;

import com.jnf.harness.domain.plugin.registry.IPluginConfigRepository;
import com.jnf.harness.infrastructure.dao.PluginConfigDao;
import com.jnf.harness.infrastructure.dao.po.PluginConfigPO;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 插件配置仓储（MyBatis 实现；layer 固定 default）。
 */
public class PluginConfigRepository implements IPluginConfigRepository {

    public static final String LAYER_DEFAULT = "default";

    private final PluginConfigDao dao;

    public PluginConfigRepository(PluginConfigDao dao) {
        this.dao = dao;
    }

    @Override
    public void upsert(String pluginId, String configKey, String configValue) {
        dao.upsert(toPo(pluginId, configKey, configValue));
    }

    @Override
    public void delete(String pluginId, String configKey) {
        dao.deleteByKey(pluginId, configKey, LAYER_DEFAULT);
    }

    @Override
    public Optional<String> find(String pluginId, String configKey) {
        PluginConfigPO po = dao.findByKey(pluginId, configKey, LAYER_DEFAULT);
        return po == null ? Optional.empty() : Optional.ofNullable(po.getConfigValue());
    }

    @Override
    public Map<String, String> findAll(String pluginId) {
        List<PluginConfigPO> rows = dao.findByPlugin(pluginId, LAYER_DEFAULT);
        Map<String, String> result = new LinkedHashMap<>();
        for (PluginConfigPO row : rows) {
            result.put(row.getConfigKey(), row.getConfigValue());
        }
        return result;
    }

    private static PluginConfigPO toPo(String pluginId, String configKey, String configValue) {
        PluginConfigPO po = new PluginConfigPO();
        po.setPluginId(pluginId);
        po.setConfigKey(configKey);
        po.setConfigLayer(LAYER_DEFAULT);
        po.setConfigValue(configValue);
        return po;
    }
}

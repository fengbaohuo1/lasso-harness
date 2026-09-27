package com.jnf.harness.infrastructure.dao.po;

/**
 * 插件配置持久化对象（harness_plugin_config）。
 */
public class PluginConfigPO {

    private String pluginId;
    private String configKey;
    private String configLayer;
    private String configValue;

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigLayer() {
        return configLayer;
    }

    public void setConfigLayer(String configLayer) {
        this.configLayer = configLayer;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }
}

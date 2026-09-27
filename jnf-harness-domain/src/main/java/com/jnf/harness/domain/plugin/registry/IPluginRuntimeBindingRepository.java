package com.jnf.harness.domain.plugin.registry;

/**
 * 插件运行绑定仓储端口。
 */
public interface IPluginRuntimeBindingRepository {

    void upsert(PluginRuntimeBindingEntity entity);
}

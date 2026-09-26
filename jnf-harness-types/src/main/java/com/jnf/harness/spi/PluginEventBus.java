package com.jnf.harness.spi;

/**
 * 插件事件总线：订阅/发布以 pluginId 为来源标识。
 */
public interface PluginEventBus {

    /** 订阅事件（eventName 支持精确名；宿主保留扩展通配的可能）。返回取消订阅器。 */
    AutoCloseable subscribe(String eventName, String subscriberPluginId, PluginEventHandler handler);

    /** 发布事件。 */
    void publish(PluginEvent event);
}

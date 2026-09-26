package com.jnf.harness.spi;

/**
 * 插件事件处理器。
 */
@FunctionalInterface
public interface PluginEventHandler {

    void handle(PluginEvent event);
}

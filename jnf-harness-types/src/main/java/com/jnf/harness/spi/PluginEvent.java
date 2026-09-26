package com.jnf.harness.spi;

import java.time.Instant;
import java.util.Objects;

/**
 * 插件事件：插件间通信与观测的载体。
 */
public record PluginEvent(String sourcePluginId, String name, Object payload, Instant timestamp) {

    public PluginEvent {
        Objects.requireNonNull(sourcePluginId, "sourcePluginId");
        Objects.requireNonNull(name, "name");
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    public static PluginEvent of(String sourcePluginId, String name, Object payload) {
        return new PluginEvent(sourcePluginId, name, payload, Instant.now());
    }
}

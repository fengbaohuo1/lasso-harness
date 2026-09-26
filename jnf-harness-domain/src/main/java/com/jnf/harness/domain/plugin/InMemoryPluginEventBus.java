package com.jnf.harness.domain.plugin;

import com.jnf.harness.spi.PluginEvent;
import com.jnf.harness.spi.PluginEventBus;
import com.jnf.harness.spi.PluginEventHandler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 内存插件事件总线（按事件名订阅，广播投递）。
 */
public class InMemoryPluginEventBus implements PluginEventBus {

    private record Subscription(String eventName, String subscriberId, PluginEventHandler handler) {
    }

    private final Map<String, List<Subscription>> subscribers = new ConcurrentHashMap<>();

    @Override
    public AutoCloseable subscribe(String eventName, String subscriberPluginId, PluginEventHandler handler) {
        Subscription subscription = new Subscription(eventName, subscriberPluginId, handler);
        subscribers.computeIfAbsent(eventName, k -> new CopyOnWriteArrayList<>()).add(subscription);
        return () -> subscribers.getOrDefault(eventName, List.of()).remove(subscription);
    }

    @Override
    public void publish(PluginEvent event) {
        for (Subscription subscription : subscribers.getOrDefault(event.name(), List.of())) {
            try {
                subscription.handler().handle(event);
            } catch (Exception ignored) {
                // 观察型订阅者不影响发布方
            }
        }
    }
}

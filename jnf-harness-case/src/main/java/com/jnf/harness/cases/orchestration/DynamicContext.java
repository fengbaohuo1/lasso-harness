package com.jnf.harness.cases.orchestration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流程用例的步骤间状态载体。
 */
public class DynamicContext {

    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private volatile boolean stopped;

    public void put(String key, Object value) {
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = attributes.get(key);
        return value == null ? null : (T) value;
    }

    public boolean has(String key) {
        return attributes.containsKey(key);
    }

    /** 终止后续节点。 */
    public void stop() {
        this.stopped = true;
    }

    public boolean isStopped() {
        return stopped;
    }
}

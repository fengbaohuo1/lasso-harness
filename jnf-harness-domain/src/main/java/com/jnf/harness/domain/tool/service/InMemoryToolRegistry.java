package com.jnf.harness.domain.tool.service;

import com.jnf.harness.model.ToolDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存工具注册表（线程安全）。
 */
public class InMemoryToolRegistry implements ToolRegistry {

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();

    @Override
    public Runnable register(ToolDefinition tool) {
        Objects.requireNonNull(tool, "tool");
        tools.put(tool.name(), tool);
        return () -> tools.remove(tool.name(), tool);
    }

    @Override
    public Optional<ToolDefinition> lookup(String toolName) {
        return Optional.ofNullable(tools.get(toolName));
    }

    @Override
    public List<ToolDefinition> list() {
        return new ArrayList<>(tools.values());
    }
}

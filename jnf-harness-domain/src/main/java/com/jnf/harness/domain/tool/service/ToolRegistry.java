package com.jnf.harness.domain.tool.service;

import com.jnf.harness.model.ToolDefinition;

import java.util.List;
import java.util.Optional;

/**
 * 工具注册表：工具名 → 工具定义（插件工具与内置工具同表登记）。
 */
public interface ToolRegistry {

    /** 注册工具，返回注销器。 */
    Runnable register(ToolDefinition tool);

    Optional<ToolDefinition> lookup(String toolName);

    List<ToolDefinition> list();
}

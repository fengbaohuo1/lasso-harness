package com.jnf.harness.spi;

import com.jnf.harness.model.ToolDefinition;

import java.util.List;
import java.util.Objects;

/**
 * 插件基类：持有 pluginId，configure() 默认把 tools() 注册进上下文。
 * <p>子类覆写 configure 时必须先调 {@code super.configure(context)}。
 */
public abstract class AbstractHarnessPlugin implements JavaHarnessPlugin {

    private final String pluginId;
    protected PluginContext context;

    protected AbstractHarnessPlugin(String pluginId) {
        this.pluginId = Objects.requireNonNull(pluginId, "pluginId");
    }

    @Override
    public String pluginId() {
        return pluginId;
    }

    @Override
    public List<ToolDefinition> tools() {
        return List.of();
    }

    @Override
    public void configure(PluginContext context) {
        this.context = Objects.requireNonNull(context, "context");
        for (ToolDefinition tool : tools()) {
            context.registerTool(tool);
        }
    }

    /** 读取宿主配置（configure 之后可用）。 */
    protected String getConfig(String key, String defaultValue) {
        return context == null ? defaultValue : context.getConfig(key, defaultValue);
    }
}

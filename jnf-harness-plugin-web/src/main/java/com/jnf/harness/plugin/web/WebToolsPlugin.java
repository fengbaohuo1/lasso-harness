package com.jnf.harness.plugin.web;

import com.jnf.harness.model.ToolDefinition;
import com.jnf.harness.spi.AbstractHarnessPlugin;
import com.jnf.harness.spi.PluginContext;
import com.jnf.harness.spi.host.WebService;

import java.util.ArrayList;
import java.util.List;

/**
 * 官方 Web 工具插件入口（P2）。
 * <p>出网通道经 {@code lookupService(WebService.class)} 取自宿主；
 * 宿主未提供服务时工具注册但降级报错。
 */
public class WebToolsPlugin extends AbstractHarnessPlugin {

    private final List<ToolDefinition> tools = new ArrayList<>();

    public WebToolsPlugin() {
        super("builtin-web");
    }

    @Override
    public List<ToolDefinition> tools() {
        return tools;
    }

    @Override
    public void configure(PluginContext context) {
        WebService web = context.lookupService(WebService.class).orElse(null);
        tools.clear();
        tools.add(new WebFetchTool(web));
        tools.add(new WebSearchTool(web));
        super.configure(context);
    }
}

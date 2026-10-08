package com.jnf.harness.plugin.shell;

import com.jnf.harness.model.ToolDefinition;
import com.jnf.harness.spi.AbstractHarnessPlugin;
import com.jnf.harness.spi.PluginContext;
import com.jnf.harness.spi.host.ShellService;

import java.util.ArrayList;
import java.util.List;

/**
 * 官方 shell 工具插件入口（P2）。
 * <p>执行通道经 {@code lookupService(ShellService.class)} 取自宿主（命令守卫 R2、
 * 超时与平台适配在宿主侧内建）；宿主未提供服务时工具注册但降级报错。
 */
public class ShellToolsPlugin extends AbstractHarnessPlugin {

    private final List<ToolDefinition> tools = new ArrayList<>();

    public ShellToolsPlugin() {
        super("builtin-shell");
    }

    @Override
    public List<ToolDefinition> tools() {
        return tools;
    }

    @Override
    public void configure(PluginContext context) {
        ShellService shell = context.lookupService(ShellService.class).orElse(null);
        tools.clear();
        tools.add(new ShellExecuteTool(shell));
        super.configure(context);
    }
}

package com.jnf.harness.domain.plugin;

import com.jnf.harness.model.ToolDefinition;
import com.jnf.harness.spi.AbstractHarnessPlugin;

import java.util.List;

/**
 * 远端工具插件适配：把 {@link IRemoteToolBridge} 的工具面包装成 JavaHarnessPlugin，
 * 使 Node Bridge / MCP 与 Java 插件走同一套注册/回收链路。
 */
public class RemoteToolsPlugin extends AbstractHarnessPlugin {

    private final IRemoteToolBridge bridge;

    public RemoteToolsPlugin(String pluginId, IRemoteToolBridge bridge) {
        super(pluginId);
        this.bridge = bridge;
    }

    @Override
    public List<ToolDefinition> tools() {
        return bridge.listTools().stream()
                .map(remote -> (ToolDefinition) new RemoteToolDefinition(bridge, remote))
                .toList();
    }
}

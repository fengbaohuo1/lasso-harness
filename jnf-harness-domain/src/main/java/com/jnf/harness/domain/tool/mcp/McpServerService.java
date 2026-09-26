package com.jnf.harness.domain.tool.mcp;

import com.jnf.harness.domain.plugin.IRemoteToolBridge;
import com.jnf.harness.domain.plugin.RemoteToolDefinition;
import com.jnf.harness.domain.tool.service.ToolRegistry;
import com.jnf.harness.model.ToolDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP Server 管理：连接 stdio/进程外 server，把远端工具以
 * {@code mcp__<serverName>__<toolName>} 注册进 ToolRegistry（与内置工具同链治理）。
 */
public class McpServerService {

    /** 一个已连接 server 的句柄（disconnect 即全部回收 + 停进程）。 */
    public static final class McpServerHandle {
        private final String name;
        private final IRemoteToolBridge bridge;
        private final List<AutoCloseable> registrations;

        McpServerHandle(String name, IRemoteToolBridge bridge, List<AutoCloseable> registrations) {
            this.name = name;
            this.bridge = bridge;
            this.registrations = registrations;
        }

        public String name() {
            return name;
        }

        public void disconnect() {
            for (AutoCloseable registration : registrations) {
                try {
                    registration.close();
                } catch (Exception ignored) {
                    // 尽力回收
                }
            }
            bridge.stop();
        }
    }

    private final ToolRegistry toolRegistry;

    public McpServerService(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /** 连接 server：start → tools/list → 注册 mcp__ 限定名工具。 */
    public McpServerHandle connect(String serverName, IRemoteToolBridge bridge) {
        if (serverName == null || serverName.isBlank()) {
            throw new IllegalArgumentException("serverName 必填");
        }
        bridge.start();
        List<AutoCloseable> registrations = new ArrayList<>();
        try {
            for (IRemoteToolBridge.RemoteTool remote : bridge.listTools()) {
                ToolDefinition tool = new McpQualifiedTool(serverName,
                        new RemoteToolDefinition(bridge, remote));
                Runnable disposer = toolRegistry.register(tool);
                registrations.add(disposer::run);
            }
            return new McpServerHandle(serverName, bridge, registrations);
        } catch (RuntimeException e) {
            for (AutoCloseable registration : registrations) {
                try {
                    registration.close();
                } catch (Exception ignored) {
                    // 尽力回收
                }
            }
            bridge.stop();
            throw e;
        }
    }

    /** mcp__<serverName>__<toolName> 限定名包装。 */
    record McpQualifiedTool(String serverName, ToolDefinition delegate) implements ToolDefinition {
        @Override
        public String name() {
            return "mcp__" + serverName + "__" + delegate.name();
        }

        @Override
        public String description() {
            return delegate.description();
        }

        @Override
        public java.util.Map<String, Object> parameters() {
            return delegate.parameters();
        }

        @Override
        public java.util.concurrent.CompletableFuture<com.jnf.harness.model.ToolExecutionResult> execute(
                java.util.Map<String, Object> arguments, com.jnf.harness.model.ToolRunContext context) {
            return delegate.execute(arguments, context);
        }
    }
}

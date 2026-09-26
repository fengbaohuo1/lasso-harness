package com.jnf.harness.domain.plugin;

import com.jnf.harness.model.ToolDefinition;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 远端工具定义：调用转发给 {@link IRemoteToolBridge}（Node/MCP 同用）。
 */
record RemoteToolDefinition(IRemoteToolBridge bridge, IRemoteToolBridge.RemoteTool remote)
        implements ToolDefinition {

    @Override
    public String name() {
        return remote.name();
    }

    @Override
    public String description() {
        return remote.description() == null ? "" : remote.description();
    }

    @Override
    public Map<String, Object> parameters() {
        return remote.parameters() == null ? Map.of("type", "object") : remote.parameters();
    }

    @Override
    public CompletableFuture<ToolExecutionResult> execute(Map<String, Object> arguments, ToolRunContext context) {
        try {
            return CompletableFuture.completedFuture(bridge.call(remote.name(), arguments));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.fail("远端工具调用失败: " + e.getMessage(), "REMOTE_TOOL_ERROR"));
        }
    }
}

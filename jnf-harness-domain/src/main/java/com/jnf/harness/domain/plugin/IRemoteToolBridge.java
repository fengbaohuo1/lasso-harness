package com.jnf.harness.domain.plugin;

import com.jnf.harness.model.ToolDefinition;
import com.jnf.harness.model.ToolExecutionResult;

import java.util.List;
import java.util.Map;

/**
 * 远端工具桥端口：Node sidecar / MCP 等"进程外工具源"的最小契约。
 */
public interface IRemoteToolBridge {

    /** 远端工具描述。 */
    record RemoteTool(String name, String description, Map<String, Object> parameters) {
    }

    void start();

    List<RemoteTool> listTools();

    ToolExecutionResult call(String name, Map<String, Object> arguments);

    void stop();
}

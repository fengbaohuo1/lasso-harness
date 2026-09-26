package com.jnf.harness.domain.sandbox;

import com.jnf.harness.domain.tool.service.ToolLifecycleHook;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.spi.PluginHookResult;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * 沙箱守卫（PRE_TOOL_USE Hook 形态接入执行链）：
 * READ_ONLY 拦写类工具；WORKSPACE_WRITE 校验 path 参数在工作区内；DANGER_FULL_ACCESS 放行。
 */
public class SandboxGuardHook implements ToolLifecycleHook {

    private static final Set<String> WRITE_TOOLS =
            Set.of("fs_write", "str_replace_editor", "fs_edit", "shell_execute");

    private final SandboxMode mode;
    private final Path workspaceRoot;

    public SandboxGuardHook(SandboxMode mode, Path workspaceRoot) {
        this.mode = mode == null ? SandboxMode.WORKSPACE_WRITE : mode;
        this.workspaceRoot = workspaceRoot == null
                ? Path.of("").toAbsolutePath() : workspaceRoot.toAbsolutePath().normalize();
    }

    public SandboxMode mode() {
        return mode;
    }

    @Override
    public PluginHookResult beforeToolUse(String toolName, Map<String, Object> arguments) {
        if (mode == SandboxMode.DANGER_FULL_ACCESS) {
            return PluginHookResult.allow();
        }
        if (mode == SandboxMode.READ_ONLY && WRITE_TOOLS.contains(toolName)) {
            return PluginHookResult.deny("沙箱 READ_ONLY 禁止写类工具: " + toolName);
        }
        if (mode == SandboxMode.WORKSPACE_WRITE && arguments != null) {
            Object path = arguments.get("path");
            if (path != null && !String.valueOf(path).isBlank()) {
                Path resolved = Path.of(String.valueOf(path));
                if (!resolved.isAbsolute()) {
                    resolved = workspaceRoot.resolve(resolved);
                }
                if (!resolved.normalize().startsWith(workspaceRoot)) {
                    return PluginHookResult.deny("沙箱 WORKSPACE_WRITE 禁止越界路径: " + path);
                }
            }
        }
        return PluginHookResult.allow();
    }

    @Override
    public void afterToolUse(String toolName, Map<String, Object> arguments, ToolExecutionResult result) {
        // 观察口：暂不需要
    }
}

package com.jnf.harness.domain.sandbox;

import com.jnf.harness.spi.PluginHookResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 沙箱三档验收（第 10 节 DoD）。
 */
class SandboxGuardHookTest {

    private final Path workspace = Path.of("/workspace").toAbsolutePath();

    @Test
    @DisplayName("READ_ONLY：写类工具全部拒绝，读类放行")
    void read_only_blocks_writes() {
        SandboxGuardHook hook = new SandboxGuardHook(SandboxMode.READ_ONLY, workspace);
        assertTrue(hook.beforeToolUse("fs_write", Map.of()).intercepted());
        assertTrue(hook.beforeToolUse("shell_execute", Map.of()).intercepted());
        assertFalse(hook.beforeToolUse("fs_read", Map.of("path", "a.txt")).intercepted());
    }

    @Test
    @DisplayName("WORKSPACE_WRITE：工作区内放行，越界路径拒绝")
    void workspace_write_bounds_paths() {
        SandboxGuardHook hook = new SandboxGuardHook(SandboxMode.WORKSPACE_WRITE, workspace);
        assertFalse(hook.beforeToolUse("fs_write", Map.of("path", "a.txt")).intercepted());
        assertFalse(hook.beforeToolUse("fs_write",
                Map.of("path", workspace.resolve("sub/b.txt").toString())).intercepted());
        assertTrue(hook.beforeToolUse("fs_write",
                Map.of("path", workspace.resolve("../escape.txt").toString())).intercepted());
        assertTrue(hook.beforeToolUse("str_replace_editor",
                Map.of("path", "/etc/passwd")).intercepted());
    }

    @Test
    @DisplayName("DANGER_FULL_ACCESS：显式全放")
    void danger_allows() {
        SandboxGuardHook hook = new SandboxGuardHook(SandboxMode.DANGER_FULL_ACCESS, workspace);
        assertFalse(hook.beforeToolUse("fs_write", Map.of("path", "/etc/x")).intercepted());
    }
}

package com.jnf.harness.plugin.sandbox;

import com.jnf.harness.model.ToolRunContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 三档权限策略验收（P9-2）：只读/工作区内读写/完全权限 + 会话级切换。
 */
class SandboxPolicyTest {

    private final Path workspace = Path.of("ws-test").toAbsolutePath().normalize();

    private ToolRunContext ctx(String sessionId) {
        return new ToolRunContext() {
            @Override
            public String agentId() {
                return "agent";
            }

            @Override
            public String sessionId() {
                return sessionId;
            }

            @Override
            public String cwd() {
                return workspace.toString();
            }
        };
    }

    @Test
    @DisplayName("READ_ONLY：写类工具与 shell 全拒，读类放行")
    void readOnly() {
        SandboxPolicy policy = new SandboxPolicy("READ_ONLY");
        assertTrue(policy.evaluate(ctx("s1"), "fs_write", Map.of("path", "a.txt")).intercepted());
        assertTrue(policy.evaluate(ctx("s1"), "shell_execute", Map.of("command", "ls")).intercepted());
        assertTrue(policy.evaluate(ctx("s1"), "plugin__fs__fs_write", Map.of("path", "a.txt")).intercepted());
        assertFalse(policy.evaluate(ctx("s1"), "fs_read", Map.of("path", "/etc/hosts")).intercepted());
    }

    @Test
    @DisplayName("WORKSPACE_WRITE：工作区内放行，越界路径/越逸命令拒绝，读不限")
    void workspaceWrite() {
        SandboxPolicy policy = new SandboxPolicy("WORKSPACE_WRITE");
        assertFalse(policy.evaluate(ctx("s1"), "fs_write", Map.of("path", "a.txt")).intercepted());
        assertFalse(policy.evaluate(ctx("s1"), "fs_write",
                Map.of("path", workspace.resolve("sub/b.txt").toString())).intercepted());
        assertTrue(policy.evaluate(ctx("s1"), "fs_write", Map.of("path", "../escape.txt")).intercepted());
        assertTrue(policy.evaluate(ctx("s1"), "fs_write",
                Map.of("path", Path.of("C:/Windows/evil.txt").toString())).intercepted());
        assertFalse(policy.evaluate(ctx("s1"), "shell_execute", Map.of("command", "mvn test")).intercepted());
        assertTrue(policy.evaluate(ctx("s1"), "shell_execute", Map.of("command", "sudo rm -rf /")).intercepted());
        assertTrue(policy.evaluate(ctx("s1"), "shell_execute", Map.of("command", "cd /tmp")).intercepted());
        assertFalse(policy.evaluate(ctx("s1"), "fs_read", Map.of("path", "C:/any/where.txt")).intercepted());
    }

    @Test
    @DisplayName("DANGER_FULL_ACCESS：全放")
    void fullAccess() {
        SandboxPolicy policy = new SandboxPolicy("DANGER_FULL_ACCESS");
        assertFalse(policy.evaluate(ctx("s1"), "fs_write", Map.of("path", "/any")).intercepted());
        assertFalse(policy.evaluate(ctx("s1"), "shell_execute", Map.of("command", "sudo x")).intercepted());
    }

    @Test
    @DisplayName("会话级切换：只影响该会话；默认档不变")
    void sessionOverride() {
        SandboxPolicy policy = new SandboxPolicy("WORKSPACE_WRITE");
        policy.setMode("s-ro", "READ_ONLY");
        assertTrue(policy.evaluate(ctx("s-ro"), "fs_write", Map.of("path", "a.txt")).intercepted());
        assertFalse(policy.evaluate(ctx("s-other"), "fs_write", Map.of("path", "a.txt")).intercepted());
        assertTrue("READ_ONLY".equals(policy.modeOf("s-ro")));
        assertTrue("WORKSPACE_WRITE".equals(policy.modeOf("s-other")));
        assertTrue("WORKSPACE_WRITE".equals(policy.defaultMode()));
    }

    @Test
    @DisplayName("缺省/未知模式归一 WORKSPACE_WRITE；空会话上下文回落默认档")
    void normalizeAndFallback() {
        assertTrue("WORKSPACE_WRITE".equals(SandboxPolicy.normalize(null)));
        assertTrue("READ_ONLY".equals(SandboxPolicy.normalize("read-only")));
        assertTrue("DANGER_FULL_ACCESS".equals(SandboxPolicy.normalize("full")));
        SandboxPolicy policy = new SandboxPolicy("READ_ONLY");
        assertTrue(policy.evaluate(null, "fs_write", Map.of("path", "a.txt")).intercepted());
    }
}

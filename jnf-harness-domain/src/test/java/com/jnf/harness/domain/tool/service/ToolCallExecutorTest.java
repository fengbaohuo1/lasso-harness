package com.jnf.harness.domain.tool.service;

import com.jnf.harness.domain.tool.fs.FsReadTool;
import com.jnf.harness.domain.tool.fs.FsWriteTool;
import com.jnf.harness.domain.tool.fs.StrReplaceEditorTool;
import com.jnf.harness.domain.tool.shell.ShellCommandGuard;
import com.jnf.harness.domain.tool.shell.ShellCommandPolicy;
import com.jnf.harness.model.ToolCallBlock;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.spi.PluginHookResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工具执行链验收（第 5 节 DoD）：成功/超时/取消/失败四态 + Hook 拦截 + 审批闸 + 工具族行为。
 */
class ToolCallExecutorTest {

    private ToolCallExecutor executor(ToolRegistry registry, List<ToolLifecycleHook> hooks,
                                      ApprovalGate gate) {
        return new ToolCallExecutor(registry, hooks, gate,
                new DefaultToolRunContext("agent-1", "s-1", "."), 1, 2);
    }

    private InMemoryToolRegistry registry(com.jnf.harness.model.ToolDefinition tool) {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(tool);
        return registry;
    }

    @Test
    @DisplayName("成功路径：查到 → Hook → 执行 → 结果")
    void executes_tool() {
        InMemoryToolRegistry registry = registry(new FsWriteTool());
        ToolCallExecutor executor = executor(registry, List.of(), ApprovalGate.ALLOW_ALL);

        ToolExecutionResult result = executor.executeBlocking(
                new ToolCallBlock("c1", "fs_write", Map.of("path", "x.txt", "content", "hi")));

        assertTrue(result.success());
        assertTrue(result.content().contains("x.txt"));
    }

    @Test
    @DisplayName("PRE Hook BLOCK：拦截跳过执行（HOOK_BLOCKED）")
    void pre_hook_blocks() {
        InMemoryToolRegistry registry = registry(new FsWriteTool());
        ToolLifecycleHook blocker = new ToolLifecycleHook() {
            @Override
            public PluginHookResult beforeToolUse(String toolName, Map<String, Object> arguments) {
                return PluginHookResult.block("测试拦截");
            }
        };
        ToolCallExecutor executor = executor(registry, List.of(blocker), ApprovalGate.ALLOW_ALL);

        ToolExecutionResult result = executor.executeBlocking(
                new ToolCallBlock("c1", "fs_write", Map.of("path", "y.txt", "content", "hi")));

        assertFalse(result.success());
        assertEquals("HOOK_BLOCKED", result.errorCode());
    }

    @Test
    @DisplayName("POST Hook 只观察：结果不被改变")
    void post_hook_observes() {
        InMemoryToolRegistry registry = registry(new FsWriteTool());
        StringBuilder observed = new StringBuilder();
        ToolLifecycleHook observer = new ToolLifecycleHook() {
            @Override
            public void afterToolUse(String toolName, Map<String, Object> arguments, ToolExecutionResult result) {
                observed.append(toolName).append(':').append(result.success());
            }
        };
        ToolCallExecutor executor = executor(registry, List.of(observer), ApprovalGate.ALLOW_ALL);

        ToolExecutionResult result = executor.executeBlocking(
                new ToolCallBlock("c1", "fs_write", Map.of("path", "z.txt", "content", "hi")));

        assertTrue(result.success());
        assertEquals("fs_write:true", observed.toString());
    }

    @Test
    @DisplayName("四态：工具不存在 / 审批拒绝 / 超时 / 结构化失败")
    void failure_modes() throws Exception {
        // 工具不存在
        ToolCallExecutor executor = executor(new InMemoryToolRegistry(), List.of(), ApprovalGate.ALLOW_ALL);
        assertEquals("TOOL_NOT_FOUND", executor.executeBlocking(
                new ToolCallBlock("c1", "nope", Map.of())).errorCode());

        // 审批拒绝
        InMemoryToolRegistry registry = registry(new FsWriteTool());
        ToolCallExecutor gated = executor(registry, List.of(), (tool, args) -> false);
        assertEquals("APPROVAL_REQUIRED", gated.executeBlocking(
                new ToolCallBlock("c1", "fs_write", Map.of("path", "a", "content", "b"))).errorCode());

        // 超时（超时 1s，工具睡 5s）
        com.jnf.harness.model.AbstractTool slow = new com.jnf.harness.model.AbstractTool() {
            @Override
            public String name() {
                return "slow";
            }

            @Override
            public String description() {
                return "慢工具";
            }

            @Override
            public Map<String, Object> parameters() {
                return objectSchema().build();
            }

            @Override
            protected CompletableFuture<com.jnf.harness.model.ToolExecutionResult> run(
                    Map<String, Object> arguments, com.jnf.harness.model.ToolRunContext context) {
                return new CompletableFuture<>(); // 永不完成
            }
        };
        InMemoryToolRegistry slowRegistry = registry(slow);
        ToolCallExecutor slowExecutor = executor(slowRegistry, List.of(), ApprovalGate.ALLOW_ALL);
        assertEquals("TOOL_TIMEOUT", slowExecutor.executeBlocking(
                new ToolCallBlock("c1", "slow", Map.of())).errorCode());

        // 工具内部异常 → 结构化失败
        com.jnf.harness.model.AbstractTool boom = new com.jnf.harness.model.AbstractTool() {
            @Override
            public String name() {
                return "boom";
            }

            @Override
            public String description() {
                return "必炸";
            }

            @Override
            public Map<String, Object> parameters() {
                return objectSchema().build();
            }

            @Override
            protected CompletableFuture<com.jnf.harness.model.ToolExecutionResult> run(
                    Map<String, Object> arguments, com.jnf.harness.model.ToolRunContext context) {
                throw new IllegalStateException("kaboom");
            }
        };
        ToolCallExecutor boomExecutor = executor(registry(boom), List.of(), ApprovalGate.ALLOW_ALL);
        assertEquals("EXECUTION_ERROR", boomExecutor.executeBlocking(
                new ToolCallBlock("c1", "boom", Map.of())).errorCode());
    }

    @Test
    @DisplayName("fs 工具族：写→读→精确替换往返")
    void fs_tools_round_trip(@TempDir Path tempDir) throws Exception {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(new FsWriteTool());
        registry.register(new FsReadTool());
        registry.register(new StrReplaceEditorTool());
        ToolCallExecutor executor = new ToolCallExecutor(registry, List.of(), ApprovalGate.ALLOW_ALL,
                new DefaultToolRunContext("a", "s", tempDir.toString()), 5, 2);

        executor.executeBlocking(new ToolCallBlock("c1", "fs_write",
                Map.of("path", "a.txt", "content", "第一行\n第二行")));
        ToolExecutionResult read = executor.executeBlocking(
                new ToolCallBlock("c2", "fs_read", Map.of("path", "a.txt")));
        assertTrue(read.content().contains("第一行"));

        ToolExecutionResult edit = executor.executeBlocking(new ToolCallBlock("c3", "str_replace_editor",
                Map.of("path", "a.txt", "old_str", "第二行", "new_str", "改后")));
        assertTrue(edit.success());
        assertEquals("改后", Files.readString(tempDir.resolve("a.txt")).lines().reduce((a, b) -> b).orElse(""));

        // 唯一性保护：重复 old_str 必须拒绝
        executor.executeBlocking(new ToolCallBlock("c4", "fs_write",
                Map.of("path", "b.txt", "content", "xx xx")));
        assertEquals("STR_NOT_UNIQUE", executor.executeBlocking(new ToolCallBlock("c5", "str_replace_editor",
                Map.of("path", "b.txt", "old_str", "xx", "new_str", "yy"))).errorCode());
    }

    @Test
    @DisplayName("R2 命令守卫：黑名单拦危险、白名单只放清单")
    void shell_command_guard() {
        ShellCommandGuard blacklistGuard = new ShellCommandGuard(ShellCommandPolicy.defaultPolicy());
        assertTrue(blacklistGuard.evaluate("ls -la").allowed());
        assertFalse(blacklistGuard.evaluate("rm -rf /").allowed());
        assertFalse(blacklistGuard.evaluate("ls; rm -rf /").allowed());

        ShellCommandGuard whitelistGuard =
                new ShellCommandGuard(ShellCommandPolicy.whitelistOf(List.of("ls", "cat")));
        assertTrue(whitelistGuard.evaluate("ls -la").allowed());
        assertFalse(whitelistGuard.evaluate("curl http://x").allowed());
    }
}

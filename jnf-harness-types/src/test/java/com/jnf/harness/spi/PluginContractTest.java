package com.jnf.harness.spi;

import com.jnf.harness.model.AbstractTool;
import com.jnf.harness.model.ToolDefinition;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约包验收测试：最小插件 + 工具能围绕契约编译并按预期协作。
 */
class PluginContractTest {

    /** 测试用假上下文：记录注册项，模拟宿主行为。 */
    static final class FakePluginContext implements PluginContext {
        final List<String> registeredTools = new ArrayList<>();
        final List<String> registrations = new ArrayList<>();

        @Override
        public String pluginId() {
            return "sample";
        }

        @Override
        public AutoCloseable registerTool(ToolDefinition tool) {
            registeredTools.add("plugin__sample__" + tool.name());
            registrations.add("tool:" + tool.name());
            return () -> registrations.remove("tool:" + tool.name());
        }

        @Override
        public void registerDisposer(AutoCloseable disposer) {
            registrations.add("disposer");
        }

        @Override
        public AutoCloseable subscribe(String eventName, PluginEventHandler handler) {
            registrations.add("subscribe:" + eventName);
            return () -> registrations.remove("subscribe:" + eventName);
        }

        @Override
        public void emit(String eventName, Object payload) {
            registrations.add("emit:" + eventName);
        }

        @Override
        public AutoCloseable registerHook(String hookPoint, PluginHook hook) {
            registrations.add("hook:" + hookPoint);
            return () -> registrations.remove("hook:" + hookPoint);
        }

        @Override
        public Optional<String> getConfig(String key) {
            return Optional.of("cfg:" + key);
        }

        @Override
        public AutoCloseable registerSystemPrompt(String name, int order, String text) {
            registrations.add("prompt:" + name);
            return () -> registrations.remove("prompt:" + name);
        }
    }

    static final class EchoTool extends AbstractTool {
        @Override
        public String name() {
            return "echo";
        }

        @Override
        public String description() {
            return "回显输入";
        }

        @Override
        public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("text", stringSchema("要回显的文本"))
                    .required("text")
                    .build();
        }

        @Override
        protected CompletableFuture<ToolExecutionResult> run(
                Map<String, Object> arguments, ToolRunContext context) {
            return ok(str(arguments, "text"));
        }
    }

    static final class SamplePlugin extends AbstractHarnessPlugin {
        SamplePlugin() {
            super("sample");
        }

        @Override
        public List<ToolDefinition> tools() {
            return List.of(new EchoTool());
        }
    }

    @Test
    @DisplayName("AbstractTool：schema 构建、执行、结构化失败三件事可用")
    void tool_basics() throws Exception {
        EchoTool tool = new EchoTool();
        Map<String, Object> schema = tool.parameters();
        assertEquals("object", schema.get("type"));
        assertTrue(schema.containsKey("properties"));

        ToolRunContext ctx = new ToolRunContext() {
            @Override
            public String agentId() {
                return "agent-1";
            }

            @Override
            public String sessionId() {
                return "session-1";
            }

            @Override
            public String cwd() {
                return ".";
            }
        };

        ToolExecutionResult ok = tool.execute(Map.of("text", "hi"), ctx).get();
        assertTrue(ok.success());
        assertEquals("hi", ok.content());

        // run 内抛异常必须收敛为结构化失败，不上抛
        AbstractTool boom = new AbstractTool() {
            @Override
            public String name() {
                return "boom";
            }

            @Override
            public String description() {
                return "总是失败";
            }

            @Override
            public Map<String, Object> parameters() {
                return objectSchema().build();
            }

            @Override
            protected CompletableFuture<ToolExecutionResult> run(
                    Map<String, Object> arguments, ToolRunContext context) {
                throw new IllegalStateException("kaboom");
            }
        };
        ToolExecutionResult failed = boom.execute(Map.of(), ctx).get();
        assertFalse(failed.success());
        assertEquals("EXECUTION_ERROR", failed.errorCode());
    }

    @Test
    @DisplayName("AbstractHarnessPlugin：configure 自动注册 tools()，PluginContext 注册口齐全")
    void plugin_configure_registers_tools() {
        FakePluginContext ctx = new FakePluginContext();
        SamplePlugin plugin = new SamplePlugin();

        plugin.configure(ctx);
        assertEquals("sample", plugin.pluginId());
        assertEquals(List.of("plugin__sample__echo"), ctx.registeredTools);

        // 五个注册口各走一遍
        ctx.registerSystemPrompt("about", 100, "sample 插件说明");
        ctx.registerHook("PRE_TOOL_USE", (toolName, args) -> PluginHookResult.allow());
        ctx.registerDisposer(() -> {
        });
        AutoCloseable unsub = ctx.subscribe("tool.called", event -> {
        });
        ctx.emit("plugin.started", Map.of());
        assertEquals("cfg:key", plugin.getConfig("key", "fallback"));

        assertTrue(ctx.registrations.contains("prompt:about"));
        assertTrue(ctx.registrations.contains("hook:PRE_TOOL_USE"));
        assertTrue(ctx.registrations.contains("emit:plugin.started"));
    }

    @Test
    @DisplayName("PluginHookResult：BLOCK/DENY 均视为拦截")
    void hook_result_semantics() {
        assertTrue(PluginHookResult.block("危险命令").intercepted());
        assertTrue(PluginHookResult.deny("拒绝").intercepted());
        assertFalse(PluginHookResult.allow().intercepted());
    }
}

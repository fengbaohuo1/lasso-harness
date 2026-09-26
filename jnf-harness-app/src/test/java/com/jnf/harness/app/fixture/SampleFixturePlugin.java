package com.jnf.harness.app.fixture;

import com.jnf.harness.model.AbstractTool;
import com.jnf.harness.model.ToolDefinition;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;
import com.jnf.harness.spi.AbstractHarnessPlugin;
import com.jnf.harness.spi.PluginContext;
import com.jnf.harness.spi.PluginHookResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 测试夹具插件：工具 + 提示词 + PRE Hook + Disposer 全注册口都演示。
 * <p>Disposer 通过写标记文件留痕（跨类加载器隔离边界可观察）。
 */
public class SampleFixturePlugin extends AbstractHarnessPlugin {

    public static final String PLUGIN_ID = "sample-fixture";

    public SampleFixturePlugin() {
        super(PLUGIN_ID);
    }

    @Override
    public List<ToolDefinition> tools() {
        return List.of(new EchoTool());
    }

    @Override
    public void configure(PluginContext context) {
        super.configure(context);
        context.registerSystemPrompt("about", 100, "sample-fixture 插件提供 echo 工具。");
        context.registerHook("PRE_TOOL_USE", (toolName, arguments) ->
                "danger".equals(toolName) ? PluginHookResult.block("夹具拦截 danger") : PluginHookResult.allow());
        context.registerDisposer(() -> context.getConfig("dispose-marker").ifPresent(marker -> {
            try {
                java.nio.file.Files.writeString(java.nio.file.Path.of(marker), "disposed");
            } catch (java.io.IOException ignored) {
                // 夹具尽力留痕
            }
        }));
    }

    public static class EchoTool extends AbstractTool {
        @Override
        public String name() {
            return "echo";
        }

        @Override
        public String description() {
            return "回显";
        }

        @Override
        public Map<String, Object> parameters() {
            return objectSchema().prop("text", stringSchema("文本")).required("text").build();
        }

        @Override
        protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> arguments, ToolRunContext context) {
            return ok(str(arguments, "text"));
        }
    }
}

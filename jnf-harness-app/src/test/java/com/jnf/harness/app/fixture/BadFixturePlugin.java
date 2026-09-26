package com.jnf.harness.app.fixture;

import com.jnf.harness.model.AbstractTool;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;
import com.jnf.harness.spi.AbstractHarnessPlugin;
import com.jnf.harness.spi.PluginContext;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 失败夹具插件：configure 中途抛异常，验证事务边界回滚。
 */
public class BadFixturePlugin extends AbstractHarnessPlugin {

    public static final String PLUGIN_ID = "bad-fixture";

    public BadFixturePlugin() {
        super(PLUGIN_ID);
    }

    @Override
    public void configure(PluginContext context) {
        super.configure(context);
        // 先注册一个工具，再失败——验证回滚会把它清掉
        context.registerTool(new AbstractTool() {
            @Override
            public String name() {
                return "ghost";
            }

            @Override
            public String description() {
                return "幽灵工具";
            }

            @Override
            public Map<String, Object> parameters() {
                return objectSchema().build();
            }

            @Override
            protected CompletableFuture<ToolExecutionResult> run(
                    Map<String, Object> arguments, ToolRunContext ctx) {
                return ok("never");
            }
        });
        throw new IllegalStateException("夹具故意失败");
    }
}

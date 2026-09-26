package com.jnf.harness.sample;

import com.jnf.harness.model.AbstractTool;
import com.jnf.harness.model.ToolDefinition;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;
import com.jnf.harness.spi.AbstractHarnessPlugin;
import com.jnf.harness.spi.PluginContext;
import com.jnf.harness.spi.PluginHookResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 示例插件：五注册口全演示。
 * <ul>
 *   <li>工具：weather_query（假数据）+ file_summary（文件摘要）；</li>
 *   <li>提示词：registerSystemPrompt 声明能力与调用时机；</li>
 *   <li>Hook：PRE_TOOL_USE 审计 shell_execute；</li>
 *   <li>事件：订阅 tool.called；</li>
 *   <li>Disposer：资源释放留痕。</li>
 * </ul>
 */
public class SampleToolsPlugin extends AbstractHarnessPlugin {

    public static final String PLUGIN_ID = "sample-tools";

    public SampleToolsPlugin() {
        super(PLUGIN_ID);
    }

    @Override
    public List<ToolDefinition> tools() {
        return List.of(new WeatherQueryTool(), new FileSummaryTool());
    }

    @Override
    public void configure(PluginContext context) {
        super.configure(context);
        context.registerSystemPrompt("about", 100,
                "## sample-tools 插件\n- weather_query 可查询城市天气（示例数据）。\n"
                        + "- file_summary 可给出文件行数/字符数摘要。");
        context.registerHook("PRE_TOOL_USE", (toolName, arguments) -> {
            if ("shell_execute".equals(toolName)) {
                // 审计留痕（放行；拦截请返回 PluginHookResult.block/deny）
                return PluginHookResult.allow();
            }
            return PluginHookResult.allow();
        });
        context.subscribe("tool.called", event -> {
            // 观测型订阅：实际项目里可做限流/统计
        });
        context.registerDisposer(() -> {
            // 连接池/线程池等资源在此释放
        });
    }

    /** 天气查询（示例返回固定数据）。 */
    public static class WeatherQueryTool extends AbstractTool {
        @Override
        public String name() {
            return "weather_query";
        }

        @Override
        public String description() {
            return "查询指定城市的天气（示例数据）。";
        }

        @Override
        public Map<String, Object> parameters() {
            return objectSchema().prop("city", stringSchema("城市名，如 Hangzhou")).required("city").build();
        }

        @Override
        protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            String city = str(args, "city");
            if (city.isBlank()) {
                return fail("city 不能为空", "MISSING_CITY");
            }
            return ok("{\"city\":\"" + city + "\",\"weather\":\"sunny\",\"temp_c\":26}");
        }
    }

    /** 文件摘要（行数/字符数）。 */
    public static class FileSummaryTool extends AbstractTool {
        @Override
        public String name() {
            return "file_summary";
        }

        @Override
        public String description() {
            return "给出文件的行数与字符数摘要。";
        }

        @Override
        public Map<String, Object> parameters() {
            return objectSchema().prop("path", stringSchema("文件路径")).required("path").build();
        }

        @Override
        protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            try {
                Path file = Path.of(str(args, "path"));
                if (!file.isAbsolute()) {
                    file = Path.of(ctx.cwd()).resolve(file).normalize();
                }
                if (!Files.exists(file)) {
                    return fail("文件不存在: " + file, "FILE_NOT_FOUND");
                }
                String content = Files.readString(file);
                return ok("lines=" + content.lines().count() + ", chars=" + content.length());
            } catch (Exception e) {
                return fail("摘要失败: " + e.getMessage(), "SUMMARY_ERROR");
            }
        }
    }
}

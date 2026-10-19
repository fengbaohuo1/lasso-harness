package com.jnf.harness.sample;

import com.jnf.harness.model.AbstractTool;
import com.jnf.harness.model.ToolDefinition;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;
import com.jnf.harness.spi.AbstractHarnessPlugin;
import com.jnf.harness.spi.PluginContext;
import com.jnf.harness.spi.PluginHookResult;
import com.jnf.harness.spi.host.PluginHttpResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 示例插件（前后端全栈模板）：注册口全演示 + UI 贡献 + 插件 REST 端点。
 * <ul>
 *   <li>工具：weather_query（示例数据）+ file_summary（文件摘要）；</li>
 *   <li>提示词：registerSystemPrompt 声明能力与调用时机；</li>
 *   <li>Hook：PRE_TOOL_USE 审计 shell_execute；</li>
 *   <li>事件：订阅 tool.called；</li>
 *   <li>Disposer：资源释放留痕；</li>
 *   <li>端点（SD3）：GET stats（工具调用计数）、POST test-connection（配置页动作按钮演示）；</li>
 *   <li>UI 清单（SD1）：META-INF/plugin-ui.json（工具卡渲染器 + 独立配置页面）。</li>
 * </ul>
 */
public class SampleToolsPlugin extends AbstractHarnessPlugin {

    public static final String PLUGIN_ID = "sample-tools";

    private static final AtomicLong WEATHER_QUERIES = new AtomicLong();
    private static final AtomicLong FILE_SUMMARIES = new AtomicLong();

    /** @ 提及示范片段（mentionProviders readEndpoint 返回内容）。 */
    private static final Map<String, String> SNIPPETS = Map.of(
            "greeting", "你好！我是 sample-tools 插件提供的问候语片段。",
            "report", "# 周报\n- 本周进展：\n- 下周计划：\n- 风险与求助：");

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
        // SD3：插件 REST 端点（/api/harness/plugins/sample-tools/api/**），UI 动作按钮打这里
        context.registerEndpoint("GET", "stats", request -> PluginHttpResponse.ok(Map.of(
                "weatherQueries", WEATHER_QUERIES.get(),
                "fileSummaries", FILE_SUMMARIES.get())));
        context.registerEndpoint("POST", "test-connection", request -> PluginHttpResponse.ok(Map.of(
                "ok", true,
                "message", "sample-tools 连接正常（演示端点）",
                "echoBody", request.bodyJson() == null ? "" : request.bodyJson())));
        // C5：@ 提及源示范（mentionProviders 契约：候选 items + 展开 content）
        context.registerEndpoint("GET", "list-snippets", request -> PluginHttpResponse.ok(Map.of(
                "items", List.of(
                        Map.of("value", "greeting", "label", "问候语模板", "description", "常用开场白"),
                        Map.of("value", "report", "label", "周报模板", "description", "周报骨架")))));
        context.registerEndpoint("GET", "read-snippet", request -> PluginHttpResponse.ok(Map.of(
                "content", SNIPPETS.getOrDefault(
                        String.valueOf(request.query().get("path")),
                        "（未知片段）"))));
    }

    /** 天气查询（示例返回固定数据；JSON 结构供 UI 渲染器消费）。 */
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
            WEATHER_QUERIES.incrementAndGet();
            return ok("{\"city\":\"" + city + "\",\"weather\":\"sunny\",\"temp_c\":26,"
                    + "\"humidity\":60,\"wind\":\"SE 3\"}");
        }
    }

    /** 文件摘要（行数/字符数；JSON 结构供 UI 渲染器消费）。 */
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
                FILE_SUMMARIES.incrementAndGet();
                long lines = content.lines().count();
                return ok("{\"path\":\"" + file.toString().replace("\\", "\\\\")
                        + "\",\"lines\":" + lines + ",\"chars\":" + content.length() + "}");
            } catch (Exception e) {
                return fail("摘要失败: " + e.getMessage(), "SUMMARY_ERROR");
            }
        }
    }
}

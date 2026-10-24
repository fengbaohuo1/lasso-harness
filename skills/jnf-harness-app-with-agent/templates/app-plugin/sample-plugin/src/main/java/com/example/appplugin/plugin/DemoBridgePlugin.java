package com.example.appplugin.plugin;

import com.jnf.harness.model.AbstractTool;
import com.jnf.harness.model.ToolDefinition;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;
import com.jnf.harness.spi.AbstractHarnessPlugin;
import com.jnf.harness.spi.PluginContext;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 插件桥接骨架：把应用 REST 注册成 Agent 工具（应用是数据源，插件只做桥）。
 *
 * <p>注册项：
 * <ul>
 *   <li>工具 {@code demo_list_items}：GET {@code {app.base-url}/api/demo/items}，把 JSON 原样返回给模型；</li>
 *   <li>系统提示词：声明「涉及真实数据必须调用工具，不要编造数字」。</li>
 * </ul>
 *
 * <p>打包后（target/sample-plugin.jar）用 scripts/install_plugin.mjs 安装，或放进底座 jar 同级 plugins/ 目录。
 * <p>应用地址从插件配置读：{@code GET/PUT /api/harness/plugins/demo-bridge/config}，键 {@code app.base-url}，
 * 不写死；改完无需重启插件（getConfig 即用即读）。
 */
public class DemoBridgePlugin extends AbstractHarnessPlugin {

    public static final String PLUGIN_ID = "demo-bridge";

    /** 工具调用应用的 HTTP 客户端：连接 3s 超时，工具调用不能拖死 Agent 回合。 */
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public DemoBridgePlugin() {
        super(PLUGIN_ID);
    }

    @Override
    public List<ToolDefinition> tools() {
        return List.of(new DemoListItemsTool());
    }

    @Override
    public void configure(PluginContext context) {
        super.configure(context); // 必须：先注册 tools()
        context.registerSystemPrompt("bridge-guide", 100,
                "## demo-bridge 插件\n"
                        + "- `demo_list_items` 查询示例应用的清单（条目/分类/价格/库存/状态）。\n"
                        + "- 用户问到条目、库存、价格、分类等真实数据时**必须**调用该工具，禁止凭记忆或编造数字；"
                        + "回答里的数字必须能在工具返回里找到。\n"
                        + "- 工具失败时如实说明原因（如应用未启动），不要改用幻觉数据。");
    }

    /** 清单工具：只调应用 REST，不直连数据库；异常分类后返回结构化失败。 */
    class DemoListItemsTool extends AbstractTool {

        @Override
        public String name() {
            return "demo_list_items";
        }

        @Override
        public String description() {
            return "查询示例应用的清单数据（GET /api/demo/items）。"
                    + "用户问条目、库存、价格、分类、汇总时调用；"
                    + "返回应用 REST 的原始 JSON 信封 {code,info,data}，data 为条目数组。参数 keyword 可选，按名称/分类过滤。";
        }

        @Override
        public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("keyword", stringSchema("可选：按名称或分类过滤的关键字，为空返回全部"))
                    .build();
        }

        @Override
        protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            String appBase = getConfig("app.base-url", "http://127.0.0.1:18081");
            String keyword = str(args, "keyword").trim();
            String url = appBase.replaceAll("/+$", "") + "/api/demo/items";
            if (!keyword.isEmpty()) {
                url += "?keyword=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            try {
                HttpResponse<String> response = HTTP.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    return fail("应用返回 HTTP " + response.statusCode() + "：" + response.body(),
                            "APP_HTTP_ERROR");
                }
                return ok(response.body());
            } catch (java.net.ConnectException e) {
                return fail("连不上应用 " + appBase + "：先启动应用（java -jar sample-app/target/sample-app.jar"
                        + " --server.port=18081），或把插件配置 app.base-url 改成实际地址", "APP_UNREACHABLE");
            } catch (java.net.http.HttpTimeoutException e) {
                return fail("调用应用超时（3s）：" + url + "；检查应用是否卡住或端口不通", "APP_TIMEOUT");
            } catch (Exception e) {
                return fail("调用应用失败：" + e.getMessage() + "（URL: " + url + "）", "APP_CALL_FAILED");
            }
        }
    }
}

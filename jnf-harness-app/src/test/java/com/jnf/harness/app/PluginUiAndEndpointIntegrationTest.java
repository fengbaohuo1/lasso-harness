package com.jnf.harness.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jnf.harness.api.response.Response;
import com.jnf.harness.domain.plugin.PluginEndpointRegistry;
import com.jnf.harness.domain.plugin.registry.PluginInstallationEntity;
import com.jnf.harness.domain.plugin.registry.PluginRegistryService;
import com.jnf.harness.spi.PluginLifecycleState;
import com.jnf.harness.trigger.http.HarnessPluginController;
import com.jnf.harness.trigger.http.PluginApiController;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第五阶段 H1~H4 验收：插件 UI 贡献清单（hasUi/独立配置页）、插件 REST 端点（注册→调用→卸载回收）、
 * UI-only 插件（无后端入口仅清单）生命周期。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = JnfHarnessApplication.class,
        properties = {"harness.auth.api-keys=test-key",
                "harness.llm.seed-default-channel=false",
                "harness.plugins.dir=target/test-plugins-ui-only",
                "harness.events.dir=target/test-events-ui",
                "spring.datasource.url=jdbc:h2:mem:dbpluginui;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"})
class PluginUiAndEndpointIntegrationTest {

    private static final Path SAMPLE_JAR = Path.of(
            "../jnf-harness-sample-plugin/target/jnf-harness-sample-plugin-0.1.0-SNAPSHOT.jar");

    @Autowired
    HarnessPluginController pluginController;

    @Autowired
    PluginApiController pluginApiController;

    @Autowired
    PluginRegistryService registry;

    @Autowired
    PluginEndpointRegistry endpointRegistry;

    @BeforeAll
    static void cleanPluginsDir() throws Exception {
        Path dir = Path.of("target/test-plugins-ui-only");
        if (Files.exists(dir)) {
            try (var paths = Files.walk(dir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    @Test
    @EnabledIf("sampleJarBuilt")
    @DisplayName("sample 全栈插件：UI 清单（渲染器+独立配置页）→ 端点调用 → 卸载后端点回收")
    void ui_manifest_and_plugin_endpoints() throws Exception {
        registry.install(new PluginInstallationEntity("sample-tools", "Sample Tools Plugin", "1.0.0",
                PluginRegistryService.RUNTIME_JAVA_NATIVE, SAMPLE_JAR.toString(), null, null));
        registry.activate("sample-tools");

        // UI 清单：toolRenderers + settingsPage（独立配置页面）+ C5 贡献点（slashCommands/mentionProviders）
        Object ui = pluginController.ui("sample-tools").data();
        assertNotNull(ui, "sample 应带 UI 贡献清单");
        String uiJson = new ObjectMapper().writeValueAsString(ui);
        assertTrue(uiJson.contains("toolRenderers"));
        assertTrue(uiJson.contains("settingsPage"));
        assertTrue(uiJson.contains("test-connection"), "配置页动作按钮应指向插件端点");
        assertTrue(uiJson.contains("slashCommands"), "sample 应示范 slashCommands 贡献点");
        assertTrue(uiJson.contains("mentionProviders"), "sample 应示范 mentionProviders 贡献点");

        // list 项带 hasUi
        Map<String, Object> item = pluginController.list().data().stream()
                .filter(i -> "sample-tools".equals(i.get("pluginId")))
                .findFirst().orElseThrow();
        assertEquals(Boolean.TRUE, item.get("hasUi"));

        // 插件端点调用（GET stats / POST test-connection）
        Response<Object> stats = pluginApiController.invoke("sample-tools",
                request("GET", "/api/harness/plugins/sample-tools/api/stats"), null);
        assertEquals("00000", stats.code());

        Response<Object> probe = pluginApiController.invoke("sample-tools",
                request("POST", "/api/harness/plugins/sample-tools/api/test-connection"), "{\"note\":\"hi\"}");
        assertEquals("00000", probe.code());

        // C5：@ 提及源示范端点（候选 items / 展开 content）
        Response<Object> snippets = pluginApiController.invoke("sample-tools",
                request("GET", "/api/harness/plugins/sample-tools/api/list-snippets"), null);
        assertEquals("00000", snippets.code());
        assertTrue(new ObjectMapper().writeValueAsString(snippets.data()).contains("greeting"));
        MockHttpServletRequest readSnippet = request("GET",
                "/api/harness/plugins/sample-tools/api/read-snippet");
        readSnippet.setParameter("path", "report");
        Response<Object> snippet = pluginApiController.invoke("sample-tools", readSnippet, null);
        assertEquals("00000", snippet.code());
        assertTrue(new ObjectMapper().writeValueAsString(snippet.data()).contains("周报"));

        // P8-2 贡献面聚合：工具/端点/UI 声明一屏看全
        Map<String, Object> contributions = pluginController.contributions("sample-tools").data();
        assertEquals(2, ((java.util.List<?>) contributions.get("tools")).size(),
                "sample 应注册 2 个工具");
        assertEquals(4, ((java.util.List<?>) contributions.get("endpoints")).size(),
                "sample 应注册 4 个端点（stats/test-connection/list-snippets/read-snippet）");
        assertTrue(String.valueOf(contributions.get("slashCommands")).contains("weather"));
        assertTrue(String.valueOf(contributions.get("mentionProviders")).contains("snippets"));
        assertEquals(Boolean.TRUE, contributions.get("hasSettingsPage"));

        // 卸载：端点随事务边界回收（classpath 官方插件的端点仍在，只验 sample 已回收）
        registry.uninstall("sample-tools");
        Response<Object> gone = pluginApiController.invoke("sample-tools",
                request("GET", "/api/harness/plugins/sample-tools/api/stats"), null);
        assertEquals("40000", gone.code());
        assertTrue(!endpointRegistry.pathExists("sample-tools", "stats"), "sample 端点注册表应清空");
    }

    @Test
    @DisplayName("C2 @file 提及源：builtin-fs list-files 候选（忽略目录生效）+ read-file 展开（越界拒）")
    void fs_mention_provider_endpoints() throws Exception {
        Path root = Files.createTempDirectory("fs-mention");
        Files.writeString(root.resolve("alpha.md"), "# alpha\n提及内容");
        Files.createDirectories(root.resolve("node_modules"));
        Files.writeString(root.resolve("node_modules/skip.js"), "ignored");

        // 候选：空 q 全量（限 20），node_modules 忽略生效
        MockHttpServletRequest list = request("GET", "/api/harness/plugins/builtin-fs/api/list-files");
        list.setParameter("cwd", root.toString());
        Response<Object> all = pluginApiController.invoke("builtin-fs", list, null);
        assertEquals("00000", all.code());
        String allJson = new ObjectMapper().writeValueAsString(all.data());
        assertTrue(allJson.contains("alpha.md"), "候选应含 alpha.md");
        assertTrue(!allJson.contains("skip.js"), "node_modules 忽略目录应生效");

        // 候选：q 过滤
        MockHttpServletRequest filtered = request("GET", "/api/harness/plugins/builtin-fs/api/list-files");
        filtered.setParameter("cwd", root.toString());
        filtered.setParameter("q", "alpha");
        Response<Object> hits = pluginApiController.invoke("builtin-fs", filtered, null);
        assertEquals("00000", hits.code());
        assertTrue(new ObjectMapper().writeValueAsString(hits.data()).contains("alpha.md"));

        // 展开：读内容
        MockHttpServletRequest read = request("GET", "/api/harness/plugins/builtin-fs/api/read-file");
        read.setParameter("cwd", root.toString());
        read.setParameter("path", "alpha.md");
        Response<Object> content = pluginApiController.invoke("builtin-fs", read, null);
        assertEquals("00000", content.code());
        assertTrue(new ObjectMapper().writeValueAsString(content.data()).contains("提及内容"));

        // 展开：越界/不存在拒绝
        MockHttpServletRequest outside = request("GET", "/api/harness/plugins/builtin-fs/api/read-file");
        outside.setParameter("cwd", root.toString());
        outside.setParameter("path", "../outside.txt");
        assertEquals("40000", pluginApiController.invoke("builtin-fs", outside, null).code());
    }

    @Test
    @DisplayName("UI-only 插件：无后端入口仅清单 → 登记/激活/取清单/卸载全生命周期")
    void ui_only_plugin_lifecycle() throws Exception {
        Path dir = Path.of("target/test-plugins-ui-only/hello-ui");
        Files.createDirectories(dir.resolve("META-INF"));
        Files.writeString(dir.resolve("META-INF/plugin.yaml"),
                "id: hello-ui\nname: Hello UI\nversion: 1.0.0\nui: META-INF/plugin-ui.json\n");
        Files.writeString(dir.resolve("META-INF/plugin-ui.json"),
                "{\"schemaVersion\":1,\"toolRenderers\":{},"
                        + "\"settingsPage\":{\"title\":\"Hello\",\"sections\":[]}}");

        registry.install(new PluginInstallationEntity("hello-ui", "Hello UI", "1.0.0",
                PluginRegistryService.RUNTIME_JAVA_NATIVE, dir.toString(), "", null));
        registry.activate("hello-ui");

        assertEquals(PluginLifecycleState.ACTIVE, registry.runtimeState("hello-ui"));
        Object ui = pluginController.ui("hello-ui").data();
        assertNotNull(ui);
        assertTrue(new ObjectMapper().writeValueAsString(ui).contains("Hello"));

        registry.uninstall("hello-ui");
        assertEquals(PluginLifecycleState.DISABLED, registry.runtimeState("hello-ui"));
    }

    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }

    static boolean sampleJarBuilt() {
        return Files.exists(SAMPLE_JAR);
    }
}

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

        // UI 清单：toolRenderers + settingsPage（独立配置页面）
        Object ui = pluginController.ui("sample-tools").data();
        assertNotNull(ui, "sample 应带 UI 贡献清单");
        String uiJson = new ObjectMapper().writeValueAsString(ui);
        assertTrue(uiJson.contains("toolRenderers"));
        assertTrue(uiJson.contains("settingsPage"));
        assertTrue(uiJson.contains("test-connection"), "配置页动作按钮应指向插件端点");

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

        // 卸载：端点随事务边界回收
        registry.uninstall("sample-tools");
        Response<Object> gone = pluginApiController.invoke("sample-tools",
                request("GET", "/api/harness/plugins/sample-tools/api/stats"), null);
        assertEquals("40000", gone.code());
        assertTrue(endpointRegistry.size() == 0, "端点注册表应清空");
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

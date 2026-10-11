package com.jnf.harness.app;

import com.jnf.harness.domain.runtime.model.IModelDiscoveryPort;
import com.jnf.harness.domain.runtime.setting.ModelSettingEntity;
import com.jnf.harness.domain.runtime.setting.ModelSettingService;
import com.jnf.harness.domain.runtime.tool.IToolCatalogPort;
import com.jnf.harness.infrastructure.dao.ToolCatalogDao;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 持久化与模型渠道验收（第 8 节 DoD）：渠道 CRUD/激活、种子幂等、工具目录接线、模型发现。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = JnfHarnessApplication.class,
        properties = {"harness.auth.api-keys=test-key",
                "harness.llm.seed-default-channel=false",
                "spring.datasource.url=jdbc:h2:mem:dbtest;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"})
class PersistenceIntegrationTest {

    @Autowired
    ModelSettingService modelSettingService;

    @Autowired
    IToolCatalogPort toolCatalogPort;

    @Autowired
    ToolCatalogDao toolCatalogDao;

    @Autowired
    IModelDiscoveryPort discovery;

    @Autowired
    DataSource dataSource;

    @Autowired
    com.jnf.harness.domain.plugin.registry.PluginConfigService pluginConfigService;

    @Autowired
    com.jnf.harness.spi.PluginConfigStore pluginConfigStore;

    private HttpServer modelsServer;

    @BeforeEach
    void setUp() throws Exception {
        modelsServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        modelsServer.createContext("/models", exchange -> {
            byte[] bytes = "{\"data\":[{\"id\":\"model-a\"},{\"id\":\"model-b\"}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        modelsServer.start();
    }

    @AfterEach
    void tearDown() {
        modelsServer.stop(0);
    }

    @Test
    @DisplayName("渠道 CRUD + 唯一激活 + 协议校验")
    void model_setting_crud_and_activate() {
        modelSettingService.save(new ModelSettingEntity("ch-a", "渠道A", "openai", "model-a",
                "http://127.0.0.1:1/v1", "KEY_A", "openai", true, false));
        modelSettingService.save(new ModelSettingEntity("ch-b", "渠道B", "anthropic", "model-b",
                "http://127.0.0.1:2", "KEY_B", "anthropic", true, false));
        assertEquals(2, modelSettingService.list().stream()
                .filter(item -> item.channelCode().equals("ch-a") || item.channelCode().equals("ch-b")).count());

        modelSettingService.activate("ch-b");
        assertEquals("ch-b", modelSettingService.active().orElseThrow().channelCode());

        // 重新保存另一渠道不影响激活；激活切换后唯一
        modelSettingService.activate("ch-a");
        assertEquals("ch-a", modelSettingService.active().orElseThrow().channelCode());
        assertEquals(1, modelSettingService.list().stream().filter(ModelSettingEntity::active).count());

        assertThrows(IllegalArgumentException.class, () -> modelSettingService.save(
                new ModelSettingEntity("bad", "x", "p", "m", "http://x", "K", "ftp", true, false)));

        modelSettingService.delete("ch-b");
        assertTrue(modelSettingService.list().stream()
                .noneMatch(item -> item.channelCode().equals("ch-b")));
    }

    @Test
    @DisplayName("M1 写入式 apiKey：进密钥库，apiKeyRef 为引用，hasApiKey/resolve 可用（R9 不回显）")
    void model_api_key_roundtrip() {
        ModelSettingEntity saved = modelSettingService.save(new ModelSettingEntity("ch-key", "渠道K", "openai",
                "model-k", "http://127.0.0.1:1/v1", "", "openai", true, false), "sk-123");
        assertEquals("secret:apiKey.ch-key", saved.apiKeyRef());
        assertTrue(modelSettingService.hasApiKey("ch-key"));
        assertEquals("sk-123", modelSettingService.resolveApiKey("ch-key"));
        modelSettingService.delete("ch-key");
    }

    @Test
    @DisplayName("M2 激活/删除保护：禁用渠道不可激活，生效渠道不可删")
    void model_setting_guards() {
        modelSettingService.save(new ModelSettingEntity("ch-off", "禁用渠道", "openai", "m",
                "http://127.0.0.1:1/v1", "", "openai", false, false));
        assertThrows(IllegalArgumentException.class, () -> modelSettingService.activate("ch-off"));

        modelSettingService.save(new ModelSettingEntity("ch-live", "生效渠道", "openai", "m",
                "http://127.0.0.1:1/v1", "", "openai", true, false));
        modelSettingService.activate("ch-live");
        assertThrows(IllegalArgumentException.class, () -> modelSettingService.delete("ch-live"));
    }

    @Test
    @DisplayName("P1 插件配置持久化：PluginConfigService 写 → PluginConfigStore（DB）读，删键生效")
    void plugin_config_persistence() {
        pluginConfigService.save("demo-plugin", java.util.Map.of("apiKey", "v1", "region", "cn"));
        assertEquals("v1", pluginConfigStore.get("demo-plugin", "apiKey").orElseThrow());
        assertEquals("cn", pluginConfigService.get("demo-plugin").get("region"));

        pluginConfigService.deleteKey("demo-plugin", "apiKey");
        assertTrue(pluginConfigStore.get("demo-plugin", "apiKey").isEmpty());
        assertEquals("cn", pluginConfigStore.get("demo-plugin", "region").orElseThrow());
    }

    @Test
    @DisplayName("种子幂等：schema.sql 跑两遍数量不变（冷启动两次不重复播种）；中文种子不乱码（编码回归）")
    void seed_is_idempotent() {
        int before = toolCatalogDao.findAll().size();
        assertTrue(before >= 11, "工具目录种子应≥11，实际 " + before);
        // 模拟第二次冷启动的 schema 初始化（显式 UTF-8，与 application.yml 的 spring.sql.init.encoding 一致）
        ResourceDatabasePopulator populator =
                new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
        populator.setSqlScriptEncoding("UTF-8");
        org.springframework.jdbc.datasource.init.DatabasePopulatorUtils.execute(populator, dataSource);
        assertEquals(before, toolCatalogDao.findAll().size());
        // 编码回归：中文 display_name/description 不能被 GBK 解码成乱码（历史 bug：SQL 初始化跟平台编码）
        com.jnf.harness.infrastructure.dao.po.ToolCatalogPO ask = toolCatalogDao.findAll().stream()
                .filter(e -> "ask_user_question".equals(e.getToolCode()))
                .findFirst().orElseThrow();
        assertEquals("向用户提问", ask.getDisplayName());
        assertEquals("澄清问题并等待回答", ask.getDescription());
    }

    @Test
    @DisplayName("R5 工具目录真接线：default 画像启用集可查")
    void tool_catalog_wired() {
        List<IToolCatalogPort.ToolCatalogEntry> enabled = toolCatalogPort.findEnabled("default");
        assertTrue(enabled.stream().anyMatch(e -> e.toolCode().equals("shell_execute")
                && e.requiresApproval()), "shell_execute 应在目录且标记需审批");
        assertEquals(enabled.size(), toolCatalogPort.findEnabled("default").size());
    }

    @Test
    @DisplayName("模型发现：解析上游 /models 列表")
    void model_discovery() {
        List<String> models = discovery.discover(
                "http://127.0.0.1:" + modelsServer.getAddress().getPort(), "k", "openai");
        assertEquals(List.of("model-a", "model-b"), models);
    }
}

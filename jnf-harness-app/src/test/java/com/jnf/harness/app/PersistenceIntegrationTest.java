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
        assertEquals(2, modelSettingService.list().size());

        modelSettingService.activate("ch-b");
        assertEquals("ch-b", modelSettingService.active().orElseThrow().channelCode());

        // 重新保存另一渠道不影响激活；激活切换后唯一
        modelSettingService.activate("ch-a");
        assertEquals("ch-a", modelSettingService.active().orElseThrow().channelCode());
        assertEquals(1, modelSettingService.list().stream().filter(ModelSettingEntity::active).count());

        assertThrows(IllegalArgumentException.class, () -> modelSettingService.save(
                new ModelSettingEntity("bad", "x", "p", "m", "http://x", "K", "ftp", true, false)));

        modelSettingService.delete("ch-b");
        assertEquals(1, modelSettingService.list().size());
    }

    @Test
    @DisplayName("种子幂等：schema.sql 跑两遍数量不变（冷启动两次不重复播种）")
    void seed_is_idempotent() {
        int before = toolCatalogDao.findAll().size();
        assertTrue(before >= 11, "工具目录种子应≥11，实际 " + before);
        // 模拟第二次冷启动的 schema 初始化
        org.springframework.jdbc.datasource.init.DatabasePopulatorUtils.execute(
                new ResourceDatabasePopulator(new ClassPathResource("schema.sql")), dataSource);
        assertEquals(before, toolCatalogDao.findAll().size());
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

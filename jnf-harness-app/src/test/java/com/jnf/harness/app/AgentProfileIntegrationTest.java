package com.jnf.harness.app;

import com.jnf.harness.api.response.Response;
import com.jnf.harness.app.config.HarnessConfiguration;
import com.jnf.harness.domain.agent.profile.AgentProfile;
import com.jnf.harness.domain.agent.profile.AgentProfileService;
import com.jnf.harness.domain.runtime.tool.IToolCatalogPort;
import com.jnf.harness.domain.tool.service.ToolRegistry;
import com.jnf.harness.trigger.http.HarnessProfileController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A1 画像验收：REST CRUD/删除保护 → 旋钮解析 → 工具画像白名单裁 schema（token 开销按场景裁）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = JnfHarnessApplication.class,
        properties = {"harness.auth.api-keys=test-key",
                "harness.llm.seed-default-channel=false",
                "harness.events.dir=target/test-events-profile",
                "spring.datasource.url=jdbc:h2:mem:dbprofile;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"})
class AgentProfileIntegrationTest {

    @Autowired
    HarnessProfileController controller;

    @Autowired
    AgentProfileService profileService;

    @Autowired
    IToolCatalogPort toolCatalogPort;

    @Autowired
    ToolRegistry toolRegistry;

    @Test
    @DisplayName("画像 CRUD → default 删除保护 → 旋钮/工具画像/补丁解析 → 白名单裁 schema")
    void profile_lifecycle_and_schema_trim() {
        // 建 faq 画像：小步数 + 窄工具面 + 场景指令
        Response<AgentProfile> saved = controller.save("faq", Map.of(
                "displayName", "FAQ 轻量画像",
                "maxStepsPerTurn", "2",
                "maxTokenContinuations", "1",
                "contextTokenBudget", "32000",
                "toolProfile", "faq-tools",
                "promptExtra", "只简答，不展开推理。"));
        assertEquals("00000", saved.code());

        AgentProfile resolved = profileService.resolve("faq");
        assertEquals(2, resolved.maxStepsPerTurn());
        assertEquals(32_000, resolved.contextTokenBudget());
        assertEquals("faq-tools", resolved.toolProfile());
        assertTrue(resolved.promptExtra().contains("简答"));
        assertEquals(50, profileService.resolve("unknown").maxStepsPerTurn(), "未知画像回落缺省");

        // default 删除保护
        assertEquals("40000", controller.delete("default").code());

        // 工具画像白名单：faq-tools 只绑 ask_user_question 一个工具
        toolCatalogPort.upsert(new IToolCatalogPort.ToolCatalogEntry(
                "ask_user_question", "向用户提问", null, "ask", false), "faq-tools");
        List<Map<String, Object>> wide = HarnessConfiguration.buildSchemas(toolCatalogPort, toolRegistry, "default");
        List<Map<String, Object>> narrow = HarnessConfiguration.buildSchemas(toolCatalogPort, toolRegistry, "faq-tools");
        assertEquals(1, narrow.size(), "窄画像只暴露白名单工具");
        assertEquals("ask_user_question", narrow.get(0).get("name"));
        assertTrue(wide.size() > narrow.size(), "宽画像工具面更大（token 开销差异即画像价值）");
    }
}

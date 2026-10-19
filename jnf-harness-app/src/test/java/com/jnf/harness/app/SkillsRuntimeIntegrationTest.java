package com.jnf.harness.app;

import com.jnf.harness.domain.skill.ISkillService;
import com.jnf.harness.domain.skill.SkillSummary;
import com.jnf.harness.domain.tool.service.ToolRegistry;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 技能运行时端到端：扫描仓库内真实技能包 → 系统提示词目录 → skill 工具按需加载正文。
 */
@SpringBootTest(classes = JnfHarnessApplication.class,
        properties = {"spring.datasource.url=jdbc:h2:mem:skillit;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                "harness.skills.roots=../skills",
                "harness.skills.user-dir=target/it-skills",
                "harness.skills.state-file=target/it-skills.properties"})
@Import(AgentApiIntegrationTest.FakeLlmConfiguration.class)
class SkillsRuntimeIntegrationTest {

    @Autowired
    ISkillService skillService;

    @Autowired
    ToolRegistry toolRegistry;

    private final ToolRunContext ctx = new ToolRunContext() {
        @Override
        public String agentId() {
            return "skill-it";
        }

        @Override
        public String sessionId() {
            return "skill-it-session";
        }

        @Override
        public String cwd() {
            return ".";
        }
    };

    @Test
    @DisplayName("仓库 skills/ 被真实发现：目录进入提示词，skill 工具返回 <skill> 包裹正文")
    void discovers_repo_skills_and_loads_body() throws Exception {
        List<SkillSummary> skills = skillService.list(".");
        List<String> names = skills.stream().map(SkillSummary::name).toList();
        assertTrue(names.contains("jnf-harness-plugin-dev"), names.toString());
        assertTrue(names.contains("jnf-harness-ops"), names.toString());
        assertTrue(names.contains("jnf-harness-demo-app"), names.toString());
        assertTrue(names.contains("skill-authoring"), names.toString());
        assertTrue(skills.stream().allMatch(SkillSummary::enabled), names.toString());
        assertTrue(skills.stream().allMatch(s -> !s.description().isBlank()), skills.toString());

        String catalog = skillService.catalog(".");
        assertTrue(catalog.startsWith("## Skills"), catalog);
        assertTrue(catalog.contains("jnf-harness-ops"), catalog);

        ToolExecutionResult loaded = toolRegistry.lookup("skill").orElseThrow()
                .execute(Map.of("name", "jnf-harness-ops"), ctx).get();
        assertTrue(loaded.success(), loaded.content());
        assertTrue(loaded.content().contains("<skill name=\"jnf-harness-ops\""), loaded.content());
        assertTrue(loaded.content().contains("Follow the skill instructions above"), loaded.content());

        // 未知技能：结构化失败并列出可用技能
        ToolExecutionResult missing = toolRegistry.lookup("skill").orElseThrow()
                .execute(Map.of("name", "no-such-skill"), ctx).get();
        assertTrue(missing.failed());
        assertTrue(missing.content().contains("jnf-harness-plugin-dev"), missing.content());
    }

    @Test
    @DisplayName("演示应用技能：正文可加载并引用 3 个案例设计卡")
    void loads_demo_app_skill_with_case_references() throws Exception {
        ToolExecutionResult loaded = toolRegistry.lookup("skill").orElseThrow()
                .execute(Map.of("name", "jnf-harness-demo-app"), ctx).get();
        assertTrue(loaded.success(), loaded.content());
        assertTrue(loaded.content().contains("<skill name=\"jnf-harness-demo-app\""), loaded.content());
        assertTrue(loaded.content().contains("case-redis-monitor.md"), loaded.content());
        assertTrue(loaded.content().contains("case-release-monitor.md"), loaded.content());
        assertTrue(loaded.content().contains("case-dev-collab.md"), loaded.content());
        // 案例设计卡真实存在于技能目录（正文引用可被 fs 工具读到）
        assertTrue(java.nio.file.Files.isRegularFile(
                java.nio.file.Path.of("../skills/jnf-harness-demo-app/references/case-redis-monitor.md")),
                "case-redis-monitor.md 应存在");
    }
}

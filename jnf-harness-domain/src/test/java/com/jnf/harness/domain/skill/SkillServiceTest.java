package com.jnf.harness.domain.skill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 技能服务：多来源合并、rank 覆盖、禁用过滤、目录生成。
 */
class SkillServiceTest {

    @Test
    @DisplayName("多来源同名技能：rank 小者胜出，列表按名排序")
    void merges_providers_by_rank() {
        SkillTestSupport.FakeProvider project = new SkillTestSupport.FakeProvider()
                .add("deploy", SkillSource.PROJECT, "project deploy", "P");
        SkillTestSupport.FakeProvider user = new SkillTestSupport.FakeProvider()
                .add("deploy", SkillSource.USER, "user deploy", "U")
                .add("review", SkillSource.USER, "user review", "R");
        SkillService service = new SkillService(List.of(user, project), new SkillTestSupport.FakeState());

        List<SkillSummary> skills = service.list(".");
        assertEquals(List.of("deploy", "review"), skills.stream().map(SkillSummary::name).toList());
        assertEquals(SkillSource.PROJECT, skills.get(0).source());
        assertEquals("project deploy", skills.get(0).description());
        assertTrue(skills.get(0).enabled());
    }

    @Test
    @DisplayName("加载正文：只采用不劣于最佳候选 rank 的定义")
    void get_uses_highest_rank_definition() {
        SkillTestSupport.FakeProvider user = new SkillTestSupport.FakeProvider()
                .add("deploy", SkillSource.USER, "user deploy", "USER-BODY");
        SkillTestSupport.FakeProvider project = new SkillTestSupport.FakeProvider()
                .add("deploy", SkillSource.PROJECT, "project deploy", "PROJECT-BODY");
        SkillService service = new SkillService(List.of(user, project), new SkillTestSupport.FakeState());

        SkillDefinition definition = service.get("deploy", ".").orElseThrow();
        assertEquals("PROJECT-BODY", definition.body());
        String rendered = definition.renderForModel();
        assertTrue(rendered.contains("<skill name=\"deploy\""));
        assertTrue(rendered.contains("Follow the skill instructions above for the remainder of this task."));
    }

    @Test
    @DisplayName("禁用技能：目录与提示词里隐藏；正文仍可按名读取（管理面）")
    void disabled_skills_are_hidden_from_catalog() {
        SkillTestSupport.FakeProvider provider = new SkillTestSupport.FakeProvider()
                .add("deploy", SkillSource.CUSTOM, "deploy it", "BODY");
        SkillTestSupport.FakeState state = new SkillTestSupport.FakeState();
        SkillService service = new SkillService(List.of(provider), state);

        assertTrue(service.catalog(".").contains("deploy"));
        state.setEnabled("deploy", false);
        assertFalse(service.catalog(".").contains("deploy"));
        assertTrue(service.catalog(".").isEmpty());
        assertFalse(service.isEnabled("deploy"));
        assertTrue(service.get("deploy", ".").isPresent());
    }

    @Test
    @DisplayName("非法名 / 未知名：get 返回空")
    void get_rejects_invalid_and_unknown_names() {
        SkillService service = new SkillService(List.of(new SkillTestSupport.FakeProvider()),
                new SkillTestSupport.FakeState());
        assertTrue(service.get("Bad Name", ".").isEmpty());
        assertTrue(service.get("missing", ".").isEmpty());
    }

    @Test
    @DisplayName("目录：含 skill 工具提示，超长描述被截断")
    void catalog_contains_loading_hint_and_truncates() {
        SkillTestSupport.FakeProvider provider = new SkillTestSupport.FakeProvider()
                .add("deploy", SkillSource.CUSTOM, "x".repeat(500), "BODY");
        SkillService service = new SkillService(List.of(provider), new SkillTestSupport.FakeState());

        String catalog = service.catalog(".");
        assertTrue(catalog.startsWith("## Skills"));
        assertTrue(catalog.contains("`skill` tool"));
        assertTrue(catalog.contains("deploy"));
        assertTrue(catalog.length() < 600, catalog);
    }
}

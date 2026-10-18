package com.jnf.harness.domain.skill;

import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * skill 工具：按名加载正文、禁用/未知/空参数的结构化失败。
 */
class SkillToolTest {

    private ToolRunContext context() {
        return new ToolRunContext() {
            @Override
            public String agentId() {
                return "a";
            }

            @Override
            public String sessionId() {
                return "s";
            }

            @Override
            public String cwd() {
                return ".";
            }
        };
    }

    @Test
    @DisplayName("加载技能：返回 <skill> 包裹的正文与后续执行提示")
    void loads_skill_body() throws Exception {
        SkillTestSupport.FakeProvider provider = new SkillTestSupport.FakeProvider()
                .add("deploy", SkillSource.CUSTOM, "deploy it", "# Deploy\nsteps");
        SkillService service = new SkillService(List.of(provider), new SkillTestSupport.FakeState());
        SkillTool tool = new SkillTool(service);

        ToolExecutionResult result = tool.execute(Map.of("name", "deploy"), context())
                .get(2, TimeUnit.SECONDS);
        assertTrue(result.success(), result.content());
        assertTrue(result.content().contains("<skill name=\"deploy\""));
        assertTrue(result.content().contains("# Deploy"));
        assertTrue(result.content().contains("Follow the skill instructions above"));
    }

    @Test
    @DisplayName("禁用技能：SKILL_NOT_FOUND，并提示当前无可用技能")
    void disabled_skill_fails() throws Exception {
        SkillTestSupport.FakeProvider provider = new SkillTestSupport.FakeProvider()
                .add("deploy", SkillSource.CUSTOM, "deploy it", "BODY");
        SkillTestSupport.FakeState state = new SkillTestSupport.FakeState();
        SkillService service = new SkillService(List.of(provider), state);
        SkillTool tool = new SkillTool(service);

        state.setEnabled("deploy", false);
        ToolExecutionResult result = tool.execute(Map.of("name", "deploy"), context())
                .get(2, TimeUnit.SECONDS);
        assertEquals("SKILL_NOT_FOUND", result.errorCode());
        assertTrue(result.content().contains("当前没有可用技能"), result.content());
    }

    @Test
    @DisplayName("空 name：SKILL_INVALID_ARGUMENT")
    void blank_name_fails() throws Exception {
        SkillTool tool = new SkillTool(new SkillService(List.of(new SkillTestSupport.FakeProvider()),
                new SkillTestSupport.FakeState()));
        ToolExecutionResult result = tool.execute(Map.of("name", "  "), context())
                .get(2, TimeUnit.SECONDS);
        assertEquals("SKILL_INVALID_ARGUMENT", result.errorCode());
    }

    @Test
    @DisplayName("未知技能：SKILL_NOT_FOUND 并列出可用技能")
    void unknown_skill_lists_available() throws Exception {
        SkillTestSupport.FakeProvider provider = new SkillTestSupport.FakeProvider()
                .add("deploy", SkillSource.CUSTOM, "deploy it", "BODY")
                .add("review", SkillSource.USER, "review it", "BODY");
        SkillService service = new SkillService(List.of(provider), new SkillTestSupport.FakeState());
        SkillTool tool = new SkillTool(service);

        ToolExecutionResult result = tool.execute(Map.of("name", "missing"), context())
                .get(2, TimeUnit.SECONDS);
        assertEquals("SKILL_NOT_FOUND", result.errorCode());
        assertTrue(result.content().contains("deploy"));
        assertTrue(result.content().contains("review"));
    }
}

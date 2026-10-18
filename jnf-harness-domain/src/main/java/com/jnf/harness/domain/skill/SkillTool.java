package com.jnf.harness.domain.skill;

import com.jnf.harness.model.AbstractTool;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * {@code skill} 工具：按名称按需加载技能正文（渐进式披露——目录常驻系统提示词，正文用时才进上下文）。
 */
public class SkillTool extends AbstractTool {

    public static final String NAME = "skill";

    private final ISkillService skillService;

    public SkillTool(ISkillService skillService) {
        this.skillService = skillService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Load a skill's full instructions by name. Skills are listed in the system prompt "
                + "under '## Skills'. When a task matches a skill's description, call this tool "
                + "first and then follow the returned instructions for the rest of the task.";
    }

    @Override
    public Map<String, Object> parameters() {
        return objectSchema()
                .prop("name", stringSchema("Skill name (kebab-case), exactly as listed in the Skills section"))
                .required("name")
                .build();
    }

    @Override
    protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext context) {
        String name = str(args, "name").strip();
        if (name.isEmpty()) {
            return fail("name 不能为空", "SKILL_INVALID_ARGUMENT");
        }
        if (!skillService.isEnabled(name)) {
            return fail("技能已禁用或不存在: " + name + "\n" + available(context.cwd()), "SKILL_NOT_FOUND");
        }
        return skillService.get(name, context.cwd())
                .<CompletableFuture<ToolExecutionResult>>map(
                        definition -> ok(definition.renderForModel()))
                .orElseGet(() -> fail("技能不存在: " + name + "\n" + available(context.cwd()), "SKILL_NOT_FOUND"));
    }

    private String available(String cwd) {
        List<SkillSummary> enabled = skillService.list(cwd).stream()
                .filter(SkillSummary::enabled)
                .toList();
        if (enabled.isEmpty()) {
            return "当前没有可用技能。";
        }
        StringBuilder sb = new StringBuilder("可用技能：");
        int limit = Math.min(enabled.size(), 50);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(enabled.get(i).name());
        }
        return sb.toString();
    }
}

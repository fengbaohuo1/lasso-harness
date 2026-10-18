package com.jnf.harness.domain.skill;

/**
 * 技能定义：元数据 + SKILL.md 正文（已剥掉 YAML frontmatter）。
 */
public record SkillDefinition(SkillCandidate candidate, String body) {

    public SkillDefinition {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate 不能为空");
        }
        body = body == null ? "" : body;
    }

    public String name() {
        return candidate.name();
    }

    public SkillSource source() {
        return candidate.source();
    }

    /**
     * 载入模型上下文时使用的文本：用 {@code <skill>} 包裹正文，并提示后续按技能指令执行。
     */
    public String renderForModel() {
        return "<skill name=\"" + candidate.name() + "\" source=\"" + candidate.source()
                + "\" path=\"" + candidate.path() + "\">\n"
                + body.strip() + "\n</skill>\n\n"
                + "Follow the skill instructions above for the remainder of this task.";
    }
}

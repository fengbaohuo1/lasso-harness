package com.jnf.harness.domain.skill;

/**
 * 技能候选（发现阶段产物）：只含元数据，正文按需加载。
 *
 * @param name        技能名（kebab-case）
 * @param description 一句话描述（进入系统提示词目录）
 * @param whenToUse   触发时机（可选）
 * @param source      来源
 * @param path        技能目录或 SKILL.md 的绝对路径（展示/排障用）
 */
public record SkillCandidate(String name, String description, String whenToUse,
                             SkillSource source, String path) {

    public SkillCandidate {
        if (!SkillNames.isValid(name)) {
            throw new IllegalArgumentException("非法技能名（需 kebab-case）: " + name);
        }
        if (source == null) {
            throw new IllegalArgumentException("技能来源不能为空");
        }
        description = description == null ? "" : description.strip();
        whenToUse = whenToUse == null ? "" : whenToUse.strip();
        path = path == null ? "" : path;
    }
}

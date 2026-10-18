package com.jnf.harness.domain.skill;

/**
 * 技能摘要（管理面/系统提示词目录用）：元数据 + 是否启用。
 */
public record SkillSummary(String name, String description, String whenToUse,
                           SkillSource source, String path, boolean enabled) {

    public SkillSummary withEnabled(boolean value) {
        return new SkillSummary(name, description, whenToUse, source, path, value);
    }
}

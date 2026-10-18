package com.jnf.harness.domain.skill;

import java.util.regex.Pattern;

/**
 * 技能命名约束：kebab-case（小写字母/数字，短横线分隔），与 SKILL.md 生态保持一致。
 */
public final class SkillNames {

    private static final Pattern PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    /** 名称最大长度。 */
    public static final int MAX_LENGTH = 64;

    private SkillNames() {
    }

    public static boolean isValid(String name) {
        return name != null && name.length() <= MAX_LENGTH && PATTERN.matcher(name).matches();
    }

    /** 从任意目录名归一化为候选技能名，非法时返回 null。 */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.strip().toLowerCase().replace('_', '-').replace(' ', '-');
        name = name.replaceAll("-{2,}", "-");
        return isValid(name) ? name : null;
    }
}

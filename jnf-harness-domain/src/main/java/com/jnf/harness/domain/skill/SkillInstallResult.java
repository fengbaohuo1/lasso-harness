package com.jnf.harness.domain.skill;

import java.nio.file.Path;

/**
 * 技能安装结果（成功/失败都以结构化返回，便于工具/REST 直接透出）。
 */
public record SkillInstallResult(boolean success, String name, String path, String message) {

    public static SkillInstallResult ok(String name, Path path, String message) {
        return new SkillInstallResult(true, name == null ? "" : name,
                path == null ? "" : path.toAbsolutePath().toString(), message);
    }

    public static SkillInstallResult fail(String message) {
        return new SkillInstallResult(false, "", "", message);
    }
}

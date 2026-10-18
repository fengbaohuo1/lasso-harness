package com.jnf.harness.domain.skill;

import java.nio.file.Path;

/**
 * 技能安装端口：从 Git 仓库 / zip 包安装到用户技能目录，或删除用户目录下的技能。
 * <p>项目级技能（随仓库分发）不可通过该端口删除。
 */
public interface ISkillInstallerPort {

    SkillInstallResult installFromGit(String gitUrl, String subdir, String name);

    SkillInstallResult installFromZip(Path zipFile, String subdir, String name);

    /** 删除用户技能目录下的技能；不存在或越界返回 false。 */
    boolean remove(String name);

    Path userSkillsDir();
}

package com.jnf.harness.domain.skill;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 技能管理服务：列表/详情/安装/启用禁用/删除，供对话工具与 REST 共用。
 */
public class SkillAdminService {

    private final ISkillService skills;
    private final ISkillInstallerPort installer;
    private final ISkillStateStore state;

    public SkillAdminService(ISkillService skills, ISkillInstallerPort installer, ISkillStateStore state) {
        this.skills = skills;
        this.installer = installer;
        this.state = state;
    }

    public List<SkillSummary> list(String cwd) {
        return skills.list(cwd);
    }

    public Optional<SkillDefinition> get(String name, String cwd) {
        return skills.get(name, cwd);
    }

    public SkillInstallResult installFromGit(String gitUrl, String subdir, String name) {
        if (gitUrl == null || gitUrl.isBlank()) {
            return SkillInstallResult.fail("gitUrl 不能为空");
        }
        return installer.installFromGit(gitUrl, subdir, name);
    }

    public SkillInstallResult installFromZip(Path zipFile, String subdir, String name) {
        if (zipFile == null) {
            return SkillInstallResult.fail("zip 文件不能为空");
        }
        return installer.installFromZip(zipFile, subdir, name);
    }

    public SkillSummary setEnabled(String name, String cwd, boolean enabled) {
        SkillSummary found = list(cwd).stream()
                .filter(skill -> skill.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("技能不存在: " + name));
        state.setEnabled(name, enabled);
        return found.withEnabled(enabled);
    }

    public void remove(String name) {
        if (!installer.remove(name)) {
            throw new IllegalArgumentException("技能不在用户技能目录（项目级/内置技能不可删除）: " + name);
        }
        state.remove(name);
    }

    public Path userSkillsDir() {
        return installer.userSkillsDir();
    }
}

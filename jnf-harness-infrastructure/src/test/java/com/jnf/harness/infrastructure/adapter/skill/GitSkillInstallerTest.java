package com.jnf.harness.infrastructure.adapter.skill;

import com.jnf.harness.domain.skill.SkillInstallResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Git/zip 技能安装器：zip 成功/失败路径、名称解析、安全边界与 remove。
 * <p>不联网：Git 只测 URL 预检，克隆路径由真实仓库在集成环境覆盖。
 */
class GitSkillInstallerTest {

    @TempDir
    Path tempDir;

    private Path userDir;
    private GitSkillInstaller installer;

    @BeforeEach
    void setUp() throws IOException {
        userDir = Files.createDirectories(tempDir.resolve("user-skills"));
        installer = new GitSkillInstaller(userDir);
    }

    @Test
    @DisplayName("zip 安装成功：frontmatter 命名、SKILL.md 与附属文件落位、返回目标路径")
    void install_from_zip_succeeds() throws IOException {
        Path zipFile = writeZip(Map.of(
                "bundle/SKILL.md", "---\nname: demo-skill\ndescription: A demo\n---\n# Demo\n",
                "bundle/notes.txt", "hello"));

        SkillInstallResult result = installer.installFromZip(zipFile, "bundle", null);

        assertTrue(result.success(), result.message());
        assertEquals("demo-skill", result.name());
        Path target = userDir.resolve("demo-skill");
        assertEquals(target.toAbsolutePath().normalize().toString(), result.path());
        assertTrue(Files.isRegularFile(target.resolve("SKILL.md")), "SKILL.md 应落位");
        assertEquals("hello", Files.readString(target.resolve("notes.txt"), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("zip 未指定 subdir 时 BFS 找到 SKILL.md，目录名作技能名")
    void install_from_zip_bfs_finds_skill() throws IOException {
        Path zipFile = writeZip(Map.of(
                "repo-main/tools/hello/SKILL.md", "# Hello\nUse hello.\n",
                "repo-main/tools/hello/tool.txt", "x"));

        SkillInstallResult result = installer.installFromZip(zipFile, null, null);

        assertTrue(result.success(), result.message());
        assertEquals("hello", result.name());
        assertTrue(Files.isRegularFile(userDir.resolve("hello").resolve("SKILL.md")));
        assertTrue(Files.isRegularFile(userDir.resolve("hello").resolve("tool.txt")));
    }

    @Test
    @DisplayName("显式 name 优先于 frontmatter name")
    void explicit_name_wins() throws IOException {
        Path zipFile = writeZip(Map.of(
                "bundle/SKILL.md", "---\nname: from-frontmatter\ndescription: x\n---\n# X\n"));

        SkillInstallResult result = installer.installFromZip(zipFile, "bundle", "explicit-name");

        assertTrue(result.success(), result.message());
        assertEquals("explicit-name", result.name());
        assertTrue(Files.isRegularFile(userDir.resolve("explicit-name").resolve("SKILL.md")));
    }

    @Test
    @DisplayName("zip 含 ../ 越界条目被拒绝且不落任何技能")
    void zip_slip_is_rejected() throws IOException {
        Path zipFile = writeZip(Map.of(
                "bundle/SKILL.md", "---\nname: evil-skill\ndescription: x\n---\n# Evil\n",
                "../evil.txt", "escape"));

        SkillInstallResult result = installer.installFromZip(zipFile, "bundle", null);

        assertFalse(result.success());
        assertTrue(result.message().contains("越界"), result.message());
        assertFalse(Files.exists(userDir.resolve("evil-skill")), "越界包不应安装");
    }

    @Test
    @DisplayName("zip 内没有 SKILL.md 时安装失败")
    void zip_without_skill_md_fails() throws IOException {
        Path zipFile = writeZip(Map.of("bundle/readme.md", "no skill here"));

        SkillInstallResult result = installer.installFromZip(zipFile, "bundle", null);

        assertFalse(result.success());
        assertTrue(result.message().contains("SKILL.md"), result.message());
    }

    @Test
    @DisplayName("目标技能已存在时安装失败")
    void existing_skill_fails() throws IOException {
        Files.createDirectories(userDir.resolve("demo-skill"));
        Path zipFile = writeZip(Map.of(
                "bundle/SKILL.md", "---\nname: demo-skill\ndescription: A demo\n---\n# Demo\n"));

        SkillInstallResult result = installer.installFromZip(zipFile, "bundle", null);

        assertFalse(result.success());
        assertTrue(result.message().contains("已存在"), result.message());
    }

    @Test
    @DisplayName("不支持的 Git URL 立即失败（不联网）")
    void unsupported_git_url_fails_fast() {
        SkillInstallResult result = installer.installFromGit("ftp://x", null, null);

        assertFalse(result.success());
        assertTrue(result.message().contains("不支持的 Git URL"), result.message());
        assertFalse(installer.installFromGit("  ", null, null).success());
    }

    @Test
    @DisplayName("remove 递归删除用户技能目录")
    void remove_deletes_user_skill() throws IOException {
        Path dir = Files.createDirectories(userDir.resolve("gone-skill").resolve("nested"));
        Files.writeString(dir.resolve("SKILL.md"), "---\ndescription: x\n---\nbody",
                StandardCharsets.UTF_8);

        assertTrue(installer.remove("gone-skill"));
        assertFalse(Files.exists(userDir.resolve("gone-skill")));
    }

    @Test
    @DisplayName("remove 不存在/非法名称/越界名称返回 false")
    void remove_missing_or_out_of_bounds_returns_false() {
        assertFalse(installer.remove("no-such-skill"));
        assertFalse(installer.remove("../escape"));
        assertFalse(installer.remove("BadName"));
        assertFalse(installer.remove(null));
    }

    @Test
    @DisplayName("userSkillsDir 返回绝对路径")
    void user_skills_dir_is_absolute() {
        assertTrue(installer.userSkillsDir().isAbsolute());
        assertEquals(userDir.toAbsolutePath().normalize(), installer.userSkillsDir());
    }

    private Path writeZip(Map<String, String> entries) throws IOException {
        Path zipFile = Files.createTempFile(tempDir, "skill-", ".zip");
        try (ZipOutputStream out = new ZipOutputStream(
                Files.newOutputStream(zipFile), StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return zipFile;
    }
}

package com.jnf.harness.infrastructure.adapter.skill;

import com.jnf.harness.domain.skill.SkillCandidate;
import com.jnf.harness.domain.skill.SkillDefinition;
import com.jnf.harness.domain.skill.SkillSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文件系统技能 provider：目录包/平铺包发现、frontmatter 解析、来源优先级、正文加载。
 */
class FilesystemSkillProviderPortTest {

    @TempDir
    Path tempDir;

    private Path userDir;
    private Path customDir;
    private Path cwd;
    private FilesystemSkillProviderPort provider;

    @BeforeEach
    void setUp() throws IOException {
        userDir = Files.createDirectories(tempDir.resolve("user-skills"));
        customDir = Files.createDirectories(tempDir.resolve("custom-skills"));
        cwd = Files.createDirectories(tempDir.resolve("workspace"));
        provider = new FilesystemSkillProviderPort(userDir, List.of(customDir));
    }

    @Test
    @DisplayName("目录包：解析 frontmatter 的 name/description/when-to-use，来源 PROJECT，path 为技能目录")
    void discovers_directory_package_with_frontmatter() throws IOException {
        Path root = projectSkillsDir();
        writeSkill(root, "git-commit", """
                ---
                name: git-commit
                description: Create git commits with conventional messages
                when-to-use: when the user asks to commit
                ---
                # Git Commit

                Do the thing.
                """);

        SkillCandidate candidate = find("git-commit");
        assertEquals("git-commit", candidate.name());
        assertEquals("Create git commits with conventional messages", candidate.description());
        assertEquals("when the user asks to commit", candidate.whenToUse());
        assertEquals(SkillSource.PROJECT, candidate.source());
        assertEquals(root.resolve("git-commit").toAbsolutePath().normalize().toString(), candidate.path());
    }

    @Test
    @DisplayName("平铺包：<name>.md 可发现，支持 BOM 与带引号的值，path 为 md 文件")
    void discovers_flat_md_package_with_bom_and_quotes() throws IOException {
        Path root = projectSkillsDir();
        Files.writeString(root.resolve("flat-skill.md"),
                "\uFEFF---\ndescription: \"Flat skill description\"\n---\nBody text.\n",
                StandardCharsets.UTF_8);

        SkillCandidate candidate = find("flat-skill");
        assertEquals("flat-skill", candidate.name());
        assertEquals("Flat skill description", candidate.description());
        assertEquals(SkillSource.PROJECT, candidate.source());
        assertEquals(root.resolve("flat-skill.md").toAbsolutePath().normalize().toString(), candidate.path());
    }

    @Test
    @DisplayName("缺 frontmatter：用正文第一条非空非标题行作描述；根下平铺 SKILL.md 被忽略")
    void missing_frontmatter_uses_first_body_line() throws IOException {
        Path root = projectSkillsDir();
        writeSkill(root, "no-frontmatter", """
                # Heading line

                Use this skill to format Java code.
                More text.
                """);
        Files.writeString(root.resolve("SKILL.md"), "---\ndescription: 根文件不是技能\n---\n",
                StandardCharsets.UTF_8);

        List<SkillCandidate> candidates = provider.discover(cwd.toString());
        assertEquals(1, candidates.size(), "根下平铺 SKILL.md 不应被当作技能");
        assertEquals("no-frontmatter", candidates.get(0).name());
        assertEquals("Use this skill to format Java code.", candidates.get(0).description());
    }

    @Test
    @DisplayName("非法名/无描述包被跳过，合法包保留")
    void invalid_names_and_missing_description_are_skipped() throws IOException {
        Path root = projectSkillsDir();
        writeSkill(root, "Bad_Name", "---\ndescription: invalid directory name\n---\nbody");
        Files.writeString(root.resolve("UPPER.md"), "---\ndescription: invalid flat name\n---\nbody",
                StandardCharsets.UTF_8);
        writeSkill(root, "no-desc", "---\nname: no-desc\n---\n# only a heading\n");
        writeSkill(root, "valid-skill", "---\ndescription: fine\n---\nbody");

        List<String> names = provider.discover(cwd.toString()).stream().map(SkillCandidate::name).toList();
        assertEquals(List.of("valid-skill"), names);
    }

    @Test
    @DisplayName("同名去重：project 覆盖 user；load 返回高优先级正文")
    void project_source_overrides_user_for_duplicate_name() throws IOException {
        writeSkill(projectSkillsDir(), "shared", "---\ndescription: project version\n---\nproject body");
        writeSkill(userDir, "shared", "---\ndescription: user version\n---\nuser body");

        List<SkillCandidate> shared = provider.discover(cwd.toString()).stream()
                .filter(candidate -> candidate.name().equals("shared"))
                .toList();
        assertEquals(1, shared.size(), "同名技能应去重");
        assertEquals("project version", shared.get(0).description());
        assertEquals(SkillSource.PROJECT, shared.get(0).source());

        SkillDefinition definition = provider.load("shared", cwd.toString()).orElseThrow();
        assertTrue(definition.body().contains("project body"));
        assertFalse(definition.body().contains("user body"));
    }

    @Test
    @DisplayName("load 剥掉 frontmatter，body 为正文并 strip")
    void load_strips_frontmatter() throws IOException {
        writeSkill(projectSkillsDir(), "strip-me", """
                ---
                name: strip-me
                description: Frontmatter should be stripped
                ---
                # Body title

                Instructions here.
                """);

        SkillDefinition definition = provider.load("strip-me", cwd.toString()).orElseThrow();
        assertEquals("strip-me", definition.name());
        assertFalse(definition.body().startsWith("---"), "正文不应保留 frontmatter 起始行");
        assertFalse(definition.body().contains("description:"), "正文不应保留 frontmatter 字段");
        assertTrue(definition.body().startsWith("# Body title"));
        assertTrue(definition.body().contains("Instructions here."));
    }

    @Test
    @DisplayName("未知技能 load 返回 Optional.empty")
    void load_unknown_returns_empty() {
        assertTrue(provider.load("does-not-exist", cwd.toString()).isEmpty());
    }

    @Test
    @DisplayName("custom root 贡献 CUSTOM 来源")
    void custom_root_contributes_custom_source() throws IOException {
        writeSkill(customDir, "custom-skill", "---\ndescription: custom one\n---\nbody");

        SkillCandidate candidate = find("custom-skill");
        assertEquals(SkillSource.CUSTOM, candidate.source());
        assertEquals("custom one", candidate.description());
    }

    @Test
    @DisplayName("description 截断到 240 字符")
    void description_is_truncated_to_240_chars() throws IOException {
        writeSkill(projectSkillsDir(), "long-desc", "# Title\n" + "x".repeat(300) + "\n");

        SkillCandidate candidate = find("long-desc");
        assertEquals(240, candidate.description().length());
    }

    @Test
    @DisplayName("cwd 为 null/空时不影响 custom 与 user 根扫描")
    void null_cwd_is_safe() throws IOException {
        writeSkill(userDir, "user-skill", "---\ndescription: user one\n---\nbody");

        SkillCandidate byNull = provider.discover(null).stream()
                .filter(candidate -> candidate.name().equals("user-skill"))
                .findFirst()
                .orElseThrow();
        assertEquals(SkillSource.USER, byNull.source());
        assertTrue(provider.load("user-skill", " ").isPresent());
    }

    private Path projectSkillsDir() throws IOException {
        return Files.createDirectories(cwd.resolve(".jnf").resolve("skills"));
    }

    private static void writeSkill(Path root, String dirName, String content) throws IOException {
        Path dir = Files.createDirectories(root.resolve(dirName));
        Files.writeString(dir.resolve("SKILL.md"), content, StandardCharsets.UTF_8);
    }

    private SkillCandidate find(String name) {
        return provider.discover(cwd.toString()).stream()
                .filter(candidate -> candidate.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未发现技能: " + name));
    }
}

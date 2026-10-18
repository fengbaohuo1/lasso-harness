package com.jnf.harness.infrastructure.adapter.skill;

import com.jnf.harness.domain.skill.ISkillProviderPort;
import com.jnf.harness.domain.skill.SkillCandidate;
import com.jnf.harness.domain.skill.SkillDefinition;
import com.jnf.harness.domain.skill.SkillNames;
import com.jnf.harness.domain.skill.SkillSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 文件系统技能发现/加载：扫描项目级、配置追加根、用户级目录下的 SKILL.md。
 * <p>包形态：
 * <ul>
 *   <li>目录包 {@code <name>/SKILL.md}，候选 path = 技能目录绝对路径；</li>
 *   <li>平铺包 {@code <name>.md}，候选 path = md 文件绝对路径（根下直接叫 SKILL.md 的平铺文件忽略）。</li>
 * </ul>
 * 同 provider 内同名去重：project &gt; custom &gt; user（先出现者胜）；不做启用态过滤（service 层负责）。
 */
public class FilesystemSkillProviderPort implements ISkillProviderPort {

    /** 技能正文最大字节数（UTF-8 原始文件大小）。 */
    static final int MAX_CONTENT_BYTES = 256 * 1024;

    /** 描述最大字符数（catalog 会再截断，这里先兜底防元数据膨胀）。 */
    static final int DESCRIPTION_MAX_CHARS = 240;

    /** 文件名 SKILL.md（目录包入口）。 */
    private static final String SKILL_FILE_NAME = "SKILL.md";

    private final Path userSkillsDir;
    private final List<Path> customRoots;

    public FilesystemSkillProviderPort(Path userSkillsDir, List<Path> customRoots) {
        this.userSkillsDir = userSkillsDir == null ? null : userSkillsDir.toAbsolutePath().normalize();
        this.customRoots = customRoots == null
                ? List.of()
                : customRoots.stream().filter(Objects::nonNull).toList();
    }

    @Override
    public List<SkillCandidate> discover(String cwd) {
        LinkedHashMap<String, SkillCandidate> byName = new LinkedHashMap<>();
        for (Root root : roots(cwd)) {
            for (ScannedSkill skill : scan(root)) {
                byName.putIfAbsent(skill.candidate().name(), skill.candidate());
            }
        }
        return List.copyOf(byName.values());
    }

    @Override
    public Optional<SkillDefinition> load(String name, String cwd) {
        if (!SkillNames.isValid(name)) {
            return Optional.empty();
        }
        for (Root root : roots(cwd)) {
            for (ScannedSkill skill : scan(root)) {
                if (!skill.candidate().name().equals(name)) {
                    continue;
                }
                Optional<SkillDefinition> loaded = readDefinition(skill);
                if (loaded.isPresent()) {
                    return loaded;
                }
            }
        }
        return Optional.empty();
    }

    /** 按优先级返回存在的根目录：project → custom（按路径排序） → user。 */
    private List<Root> roots(String cwd) {
        List<Root> roots = new ArrayList<>(4);
        Path projectRoot = projectRoot(cwd);
        if (projectRoot != null && Files.isDirectory(projectRoot)) {
            roots.add(new Root(projectRoot, SkillSource.PROJECT));
        }
        List<Path> customs = new ArrayList<>();
        for (Path custom : customRoots) {
            Path resolved = resolveCustomRoot(custom);
            if (resolved != null) {
                customs.add(resolved);
            }
        }
        customs.sort(Comparator.comparing(path -> path.toAbsolutePath().normalize().toString()));
        for (Path custom : customs) {
            if (Files.isDirectory(custom)) {
                roots.add(new Root(custom.toAbsolutePath().normalize(), SkillSource.CUSTOM));
            }
        }
        if (userSkillsDir != null && Files.isDirectory(userSkillsDir)) {
            roots.add(new Root(userSkillsDir, SkillSource.USER));
        }
        return roots;
    }

    private static Path projectRoot(String cwd) {
        if (cwd == null || cwd.isBlank()) {
            return null;
        }
        try {
            Path path = Path.of(cwd);
            if (!Files.isDirectory(path)) {
                return null;
            }
            return path.toAbsolutePath().normalize().resolve(".jnf").resolve("skills");
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Path resolveCustomRoot(Path custom) {
        try {
            return custom.isAbsolute()
                    ? custom.normalize()
                    : Path.of("").toAbsolutePath().resolve(custom).normalize();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 列出一个根目录下的全部技能包；目录遍历失败才抛 UncheckedIOException。 */
    private List<ScannedSkill> scan(Root root) {
        List<Path> entries;
        try (Stream<Path> stream = Files.list(root.dir())) {
            entries = stream.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("技能目录读取失败: " + root.dir(), e);
        }
        List<ScannedSkill> found = new ArrayList<>();
        for (Path entry : entries) {
            ScannedSkill skill = scanEntry(root, entry);
            if (skill != null) {
                found.add(skill);
            }
        }
        return found;
    }

    /** 解析单个目录包/平铺包；任何条目级失败都跳过（返回 null）。 */
    private ScannedSkill scanEntry(Root root, Path entry) {
        try {
            Path skillFile;
            Path packagePath;
            String fallbackName;
            if (Files.isDirectory(entry)) {
                skillFile = entry.resolve(SKILL_FILE_NAME);
                if (!Files.isRegularFile(skillFile)) {
                    return null;
                }
                packagePath = entry;
                fallbackName = entry.getFileName().toString();
            } else if (Files.isRegularFile(entry)) {
                String fileName = entry.getFileName().toString();
                if (!fileName.endsWith(".md") || SKILL_FILE_NAME.equals(fileName)) {
                    return null;
                }
                skillFile = entry;
                packagePath = entry;
                fallbackName = fileName.substring(0, fileName.length() - ".md".length());
            } else {
                return null;
            }
            if (!inside(root.dir(), skillFile) || !inside(root.dir(), packagePath)) {
                return null;
            }
            String text;
            try {
                text = Files.readString(skillFile, StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException e) {
                return null;
            }
            SkillMarkdown.Parsed parsed = SkillMarkdown.parse(text);
            String name = parsed.field(SkillMarkdown.FIELD_NAME);
            if (!SkillNames.isValid(name)) {
                name = fallbackName;
            }
            if (!SkillNames.isValid(name)) {
                return null;
            }
            String description = parsed.field(SkillMarkdown.FIELD_DESCRIPTION);
            if (description.isBlank()) {
                description = SkillMarkdown.firstBodyLine(parsed.body());
            }
            description = truncate(description, DESCRIPTION_MAX_CHARS);
            if (description.isBlank()) {
                return null;
            }
            String whenToUse = parsed.field("when_to_use", "when-to-use", "whentouse");
            SkillCandidate candidate = new SkillCandidate(name, description, whenToUse,
                    root.source(), packagePath.toAbsolutePath().normalize().toString());
            return new ScannedSkill(candidate, skillFile.toAbsolutePath().normalize(), root.dir());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 读取正文：超限抛 IllegalStateException；条目级 IO 失败返回空（继续尝试低优先级来源）。 */
    private Optional<SkillDefinition> readDefinition(ScannedSkill skill) {
        Path file = skill.skillFile();
        if (!inside(skill.rootDir(), file)) {
            return Optional.empty();
        }
        try {
            if (Files.size(file) > MAX_CONTENT_BYTES) {
                throw tooLarge(file);
            }
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length > MAX_CONTENT_BYTES) {
                throw tooLarge(file);
            }
            SkillMarkdown.Parsed parsed = SkillMarkdown.parse(new String(bytes, StandardCharsets.UTF_8));
            return Optional.of(new SkillDefinition(skill.candidate(), parsed.body().strip()));
        } catch (IllegalStateException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static IllegalStateException tooLarge(Path file) {
        return new IllegalStateException("技能正文超过 256KB 上限: " + file.toAbsolutePath().normalize());
    }

    private static boolean inside(Path rootDir, Path path) {
        Path root = rootDir.toAbsolutePath().normalize();
        return path.toAbsolutePath().normalize().startsWith(root);
    }

    private static String truncate(String text, int maxChars) {
        String value = text == null ? "" : text.strip();
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }

    private record Root(Path dir, SkillSource source) {
    }

    private record ScannedSkill(SkillCandidate candidate, Path skillFile, Path rootDir) {
    }
}

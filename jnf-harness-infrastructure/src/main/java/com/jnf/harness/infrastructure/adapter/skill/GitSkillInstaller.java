package com.jnf.harness.infrastructure.adapter.skill;

import com.jnf.harness.domain.skill.ISkillInstallerPort;
import com.jnf.harness.domain.skill.SkillInstallResult;
import com.jnf.harness.domain.skill.SkillNames;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Git / zip 技能安装器：把远程仓库或压缩包中的技能复制到用户技能目录。
 * <p>安全边界：仅允许 http(s)/git@/ssh 协议；git clone 超时 120s；zip 解压防 zip-slip 并限制条目数/总量；
 * 复制拒绝符号链接并按文件数/单文件/总量限额。所有失败以 {@link SkillInstallResult#fail(String)} 返回，不抛堆栈给调用方。
 */
public class GitSkillInstaller implements ISkillInstallerPort {

    /** git clone 超时（秒）。 */
    static final int GIT_CLONE_TIMEOUT_SECONDS = 120;

    /** 技能目录内文件数上限。 */
    static final int MAX_SKILL_FILES = 500;

    /** 技能目录内单文件大小上限（字节）。 */
    static final long MAX_SKILL_FILE_BYTES = 8L * 1024 * 1024;

    /** 技能目录复制总量上限（字节）。 */
    static final long MAX_SKILL_TOTAL_BYTES = 32L * 1024 * 1024;

    /** zip 条目数上限。 */
    static final int MAX_ZIP_ENTRIES = 1000;

    /** zip 解压总量上限（字节）。 */
    static final long MAX_ZIP_EXTRACT_BYTES = 64L * 1024 * 1024;

    /** git clone 失败时回显的输出长度。 */
    static final int MAX_GIT_OUTPUT_CHARS = 500;

    /** BFS 查找 SKILL.md 的最大深度（仓库根为 0）。 */
    static final int MAX_BFS_DEPTH = 4;

    private static final String SKILL_FILE_NAME = "SKILL.md";
    private static final int FRONTMATTER_HEAD_BYTES = 16 * 1024;

    private final Path userSkillsDir;

    public GitSkillInstaller(Path userSkillsDir) {
        this.userSkillsDir = Objects.requireNonNull(userSkillsDir, "userSkillsDir");
    }

    @Override
    public SkillInstallResult installFromGit(String gitUrl, String subdir, String name) {
        String url = gitUrl == null ? "" : gitUrl.strip();
        if (url.isEmpty()) {
            return SkillInstallResult.fail("Git URL 不能为空");
        }
        if (!isSupportedGitUrl(url)) {
            return SkillInstallResult.fail("不支持的 Git URL: " + url);
        }
        Path tmp = null;
        try {
            tmp = Files.createTempDirectory("jnf-skill-git-");
            Path repo = tmp.resolve("repo");
            Path log = tmp.resolve("git-clone.log");
            ProcessBuilder builder = new ProcessBuilder(
                    "git", "clone", "--depth", "1", "--", url, repo.toString());
            builder.environment().put("GIT_TERMINAL_PROMPT", "0");
            builder.redirectErrorStream(true);
            builder.redirectOutput(log.toFile());
            Process process = builder.start();
            boolean finished = process.waitFor(GIT_CLONE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return SkillInstallResult.fail("git clone 超时");
            }
            int exit = process.exitValue();
            if (exit != 0) {
                return SkillInstallResult.fail("git clone 失败(exit=" + exit + "): "
                        + firstChars(readText(log), MAX_GIT_OUTPUT_CHARS));
            }
            if (!Files.isDirectory(repo)) {
                return SkillInstallResult.fail("git clone 未产生仓库目录: " + url);
            }
            return installFromTree(repo, subdir, name);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SkillInstallResult.fail("git clone 被中断");
        } catch (IOException e) {
            return SkillInstallResult.fail("git clone 失败: " + messageOf(e));
        } catch (RuntimeException e) {
            return SkillInstallResult.fail(messageOf(e));
        } finally {
            deleteQuietly(tmp);
        }
    }

    @Override
    public SkillInstallResult installFromZip(Path zipFile, String subdir, String name) {
        if (zipFile == null || !Files.isRegularFile(zipFile)) {
            return SkillInstallResult.fail("zip 文件不存在: " + zipFile);
        }
        Path tmp = null;
        try {
            tmp = Files.createTempDirectory("jnf-skill-zip-");
            Path extractDir = tmp.resolve("zip");
            Files.createDirectories(extractDir);
            extractZip(zipFile, extractDir);
            return installFromTree(extractDir, subdir, name);
        } catch (InstallFailure e) {
            return SkillInstallResult.fail(e.getMessage());
        } catch (IOException e) {
            return SkillInstallResult.fail("zip 安装失败: " + messageOf(e));
        } catch (RuntimeException e) {
            return SkillInstallResult.fail(messageOf(e));
        } finally {
            deleteQuietly(tmp);
        }
    }

    @Override
    public boolean remove(String name) {
        if (!SkillNames.isValid(name)) {
            return false;
        }
        Path base = userSkillsDir.toAbsolutePath().normalize();
        Path target = base.resolve(name).normalize();
        if (target.equals(base) || !target.startsWith(base)) {
            return false;
        }
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try {
            deleteRecursively(target);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    @Override
    public Path userSkillsDir() {
        return userSkillsDir.toAbsolutePath().normalize();
    }

    /** 在解压/克隆出来的目录树中定位技能目录，命名并复制到用户技能目录。 */
    private SkillInstallResult installFromTree(Path baseDir, String subdir, String name) {
        Path skillDir = locateSkillDir(baseDir, subdir);
        String skillName = resolveSkillName(skillDir, name);
        Path base = userSkillsDir.toAbsolutePath().normalize();
        Path target = base.resolve(skillName).normalize();
        if (target.equals(base) || !target.startsWith(base)) {
            throw new InstallFailure("非法技能名: " + skillName);
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return SkillInstallResult.fail("技能已存在: " + skillName);
        }
        try {
            Files.createDirectories(base);
            copySkillTree(skillDir, target);
        } catch (IOException e) {
            deleteQuietly(target);
            return SkillInstallResult.fail("复制技能失败: " + messageOf(e));
        } catch (RuntimeException e) {
            deleteQuietly(target);
            throw e;
        }
        return SkillInstallResult.ok(skillName, target, "已安装技能 " + skillName);
    }

    private Path locateSkillDir(Path baseDir, String subdir) {
        Path normalizedBase = baseDir.toAbsolutePath().normalize();
        if (subdir != null && !subdir.isBlank()) {
            Path resolved;
            try {
                resolved = normalizedBase.resolve(subdir.strip()).normalize();
            } catch (InvalidPathException e) {
                throw new InstallFailure("非法的子目录: " + subdir);
            }
            if (!resolved.startsWith(normalizedBase)) {
                throw new InstallFailure("子目录越界: " + subdir);
            }
            if (!Files.isRegularFile(resolved.resolve(SKILL_FILE_NAME))) {
                throw new InstallFailure("子目录下未找到 SKILL.md: " + subdir);
            }
            return resolved;
        }
        Path found = bfsFindSkillDir(normalizedBase);
        if (found == null) {
            throw new InstallFailure("未找到 SKILL.md（搜索深度 " + MAX_BFS_DEPTH + "）");
        }
        return found;
    }

    /** 广度优先查找第一个含 SKILL.md 的目录（仓库根为深度 0，跳过 .git 与符号链接目录）。 */
    private Path bfsFindSkillDir(Path root) {
        Deque<Node> queue = new ArrayDeque<>();
        queue.addLast(new Node(root, 0));
        while (!queue.isEmpty()) {
            Node node = queue.removeFirst();
            Path dir = node.dir();
            if (Files.isRegularFile(dir.resolve(SKILL_FILE_NAME))) {
                return dir;
            }
            if (node.depth() >= MAX_BFS_DEPTH) {
                continue;
            }
            List<Path> children;
            try (Stream<Path> stream = Files.list(dir)) {
                children = stream
                        .filter(Files::isDirectory)
                        .filter(path -> !Files.isSymbolicLink(path))
                        .filter(path -> !".git".equals(path.getFileName().toString()))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .toList();
            } catch (IOException e) {
                throw new InstallFailure("技能目录遍历失败: " + dir + ": " + messageOf(e));
            }
            for (Path child : children) {
                queue.addLast(new Node(child, node.depth() + 1));
            }
        }
        return null;
    }

    /** 技能名：显式 name（须合法） &gt; frontmatter name &gt; 技能目录名，后两者经 normalize。 */
    private String resolveSkillName(Path skillDir, String explicitName) {
        if (explicitName != null && !explicitName.isBlank()) {
            String candidate = explicitName.strip();
            if (!SkillNames.isValid(candidate)) {
                throw new InstallFailure("非法技能名: " + candidate);
            }
            return candidate;
        }
        String fromFrontmatter = readFrontmatterName(skillDir.resolve(SKILL_FILE_NAME));
        String normalized = SkillNames.normalize(fromFrontmatter);
        if (normalized != null) {
            return normalized;
        }
        Path dirName = skillDir.getFileName();
        normalized = SkillNames.normalize(dirName == null ? null : dirName.toString());
        if (normalized != null) {
            return normalized;
        }
        throw new InstallFailure("无法确定技能名（frontmatter 与目录名均非法）: " + skillDir);
    }

    private static String readFrontmatterName(Path skillFile) {
        try (InputStream in = Files.newInputStream(skillFile)) {
            byte[] head = in.readNBytes(FRONTMATTER_HEAD_BYTES);
            return SkillMarkdown.parse(new String(head, StandardCharsets.UTF_8)).field(SkillMarkdown.FIELD_NAME);
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    /** 复制技能目录：先校验限额/符号链接/.git，再落盘；目标路径必须位于 target 内。 */
    private void copySkillTree(Path sourceRoot, Path targetRoot) throws IOException {
        Path source = sourceRoot.toAbsolutePath().normalize();
        Path target = targetRoot.toAbsolutePath().normalize();
        List<Path> dirs = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        long[] totalBytes = {0L};
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (Files.isSymbolicLink(dir)) {
                    throw new InstallFailure("技能目录包含符号链接，拒绝安装: " + display(source, dir));
                }
                if (!dir.equals(source) && ".git".equals(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                dirs.add(dir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (Files.isSymbolicLink(file)) {
                    throw new InstallFailure("技能目录包含符号链接，拒绝安装: " + display(source, file));
                }
                if (!attrs.isRegularFile()) {
                    throw new InstallFailure("技能目录包含不支持的文件类型: " + display(source, file));
                }
                long size = attrs.size();
                if (size > MAX_SKILL_FILE_BYTES) {
                    throw new InstallFailure("技能文件超过 8MB 上限: " + display(source, file));
                }
                totalBytes[0] += size;
                if (totalBytes[0] > MAX_SKILL_TOTAL_BYTES) {
                    throw new InstallFailure("技能目录总量超过 32MB 上限");
                }
                files.add(file);
                if (files.size() > MAX_SKILL_FILES) {
                    throw new InstallFailure("技能文件数超过 " + MAX_SKILL_FILES + " 上限");
                }
                return FileVisitResult.CONTINUE;
            }
        });
        Files.createDirectories(target);
        for (Path dir : dirs) {
            Files.createDirectories(safeTarget(target, source.relativize(dir)));
        }
        for (Path file : files) {
            Path dest = safeTarget(target, source.relativize(file));
            Path parent = dest.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path safeTarget(Path targetRoot, Path relative) {
        Path dest = targetRoot.resolve(relative).normalize();
        if (!dest.startsWith(targetRoot)) {
            throw new InstallFailure("目标路径越界: " + relative);
        }
        return dest;
    }

    private void extractZip(Path zipFile, Path extractDir) throws IOException {
        Path base = extractDir.toAbsolutePath().normalize();
        int entries = 0;
        long totalBytes = 0L;
        byte[] buffer = new byte[8192];
        try (ZipInputStream in = new ZipInputStream(
                new BufferedInputStream(Files.newInputStream(zipFile)), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ZIP_ENTRIES) {
                    throw new InstallFailure("zip 条目数超过 " + MAX_ZIP_ENTRIES + " 上限");
                }
                Path target = safeZipTarget(base, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                try (OutputStream out = Files.newOutputStream(target)) {
                    int read;
                    while ((read = in.read(buffer)) >= 0) {
                        totalBytes += read;
                        if (totalBytes > MAX_ZIP_EXTRACT_BYTES) {
                            throw new InstallFailure("zip 解压总量超过 64MB 上限");
                        }
                        out.write(buffer, 0, read);
                    }
                }
            }
        }
    }

    /** zip slip 防护：条目解析后必须仍在解压目录内。 */
    private static Path safeZipTarget(Path base, String entryName) {
        if (entryName == null || entryName.isBlank()) {
            throw new InstallFailure("zip 条目名称为空");
        }
        Path target;
        try {
            target = base.resolve(entryName).normalize();
        } catch (InvalidPathException e) {
            throw new InstallFailure("zip 条目路径非法: " + entryName);
        }
        if (!target.startsWith(base)) {
            throw new InstallFailure("zip 包含越界路径: " + entryName);
        }
        return target;
    }

    private static boolean isSupportedGitUrl(String url) {
        return url.startsWith("https://")
                || url.startsWith("http://")
                || url.startsWith("git@")
                || url.startsWith("ssh://");
    }

    private static String display(Path root, Path path) {
        try {
            return root.relativize(path).toString();
        } catch (RuntimeException e) {
            return path.toString();
        }
    }

    private static String readText(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    private static String firstChars(String text, int maxChars) {
        String value = text == null ? "" : text.strip();
        return value.length() <= maxChars ? value : value.substring(0, maxChars) + "…";
    }

    private static String messageOf(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private static void deleteQuietly(Path path) {
        if (path == null || !Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            deleteRecursively(path);
        } catch (IOException | RuntimeException ignored) {
            // 临时目录清理失败不影响安装结果
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private record Node(Path dir, int depth) {
    }

    /** 安装过程中的语义/限额失败，统一在公共入口收敛为 fail 结果。 */
    private static final class InstallFailure extends RuntimeException {
        InstallFailure(String message) {
            super(message);
        }
    }
}

package com.jnf.harness.plugin.fs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 文件提及（@file，C2）的候选枚举与内容读取：供 mentionProvider 端点（list-files/read-file）使用。
 * <p>约束（防枚举爆炸）：深度 ≤6、忽略 node_modules/target/.git 等目录、候选 ≤20 条、读取 8KB 截断。
 */
final class FsFileIndex {

    /** 枚举忽略目录。 */
    static final Set<String> IGNORED_DIRS = Set.of(
            "node_modules", "target", "dist", "build", ".git", ".idea", ".vscode",
            ".gradle", ".mvn", "__pycache__", ".venv", "venv");

    static final int MAX_DEPTH = 6;
    static final int MAX_RESULTS = 20;
    static final int MAX_READ_CHARS = 8 * 1024;

    private FsFileIndex() {
    }

    /**
     * 文件候选：相对路径大小写不敏感包含 query（空 query = 最近的浅层文件优先）。
     * 排序：命中文件名 > 命中路径，再按路径深度/字典序。
     */
    static List<Map<String, Object>> list(Path root, String query, int limit) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Path> matched = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root, MAX_DEPTH)) {
            walk.filter(Files::isRegularFile)
                    .filter(FsFileIndex::notIgnored)
                    .forEach(file -> {
                        String rel = toRel(root, file);
                        if (needle.isEmpty() || rel.toLowerCase(Locale.ROOT).contains(needle)) {
                            matched.add(file);
                        }
                    });
        } catch (IOException e) {
            return List.of();
        }
        matched.sort(Comparator
                .comparing((Path file) -> fileNameHit(root, file, needle) ? 0 : 1)
                .thenComparingInt(file -> toRel(root, file).length())
                .thenComparing(file -> toRel(root, file)));
        List<Map<String, Object>> items = new ArrayList<>();
        for (Path file : matched) {
            if (items.size() >= limit) {
                break;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            String rel = toRel(root, file);
            item.put("value", rel);
            item.put("label", rel);
            item.put("description", describe(root, file));
            items.add(item);
        }
        return items;
    }

    /**
     * 读取文件内容（8KB 截断）。相对路径基于 root；root 内路径越界拒绝（提及面不做任意盘读取）。
     *
     * @return null = 不可读/越界
     */
    static Map<String, Object> read(Path root, String path, int maxChars) {
        if (path == null || path.isBlank()) {
            return null;
        }
        Path file = Path.of(path.trim());
        if (!file.isAbsolute()) {
            file = root.resolve(file);
        }
        try {
            file = file.normalize().toRealPath();
            Path rootReal = root.toRealPath();
            if (!file.startsWith(rootReal) || !Files.isRegularFile(file)) {
                return null;
            }
            String content = Files.readString(file, StandardCharsets.UTF_8);
            boolean truncated = content.length() > maxChars;
            if (truncated) {
                content = content.substring(0, maxChars) + "\n…（已截断，全文见 fs_read）";
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", content);
            result.put("path", toRel(rootReal, file));
            result.put("truncated", truncated);
            return result;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static boolean notIgnored(Path file) {
        for (Path segment : file) {
            String name = segment.toString().toLowerCase(Locale.ROOT);
            if (IGNORED_DIRS.contains(name)) {
                return false;
            }
        }
        return true;
    }

    private static boolean fileNameHit(Path root, Path file, String needle) {
        return !needle.isEmpty()
                && file.getFileName().toString().toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String toRel(Path root, Path file) {
        Path rel = root.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize());
        return rel.toString().replace('\\', '/');
    }

    private static String describe(Path root, Path file) {
        try {
            long size = Files.size(file);
            return size < 1024 ? size + " B" : String.format(Locale.ROOT, "%.1f KB", size / 1024.0);
        } catch (IOException e) {
            return "";
        }
    }
}

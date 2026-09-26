package com.jnf.harness.domain.tool.fs;

import com.jnf.harness.model.AbstractTool;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * 按 glob 模式搜索文件名（结果上限防爆上下文）。
 */
public class FsSearchTool extends AbstractTool {

    private static final int MAX_RESULTS = 200;

    @Override
    public String name() {
        return "fs_search";
    }

    @Override
    public String description() {
        return "按 glob 模式（如 **/*.java）在目录下搜索文件路径，最多返回 200 条。";
    }

    @Override
    public Map<String, Object> parameters() {
        return objectSchema()
                .prop("pattern", stringSchema("glob 匹配模式"))
                .prop("path", stringSchema("搜索根目录（可选，默认工作目录）"))
                .required("pattern")
                .build();
    }

    @Override
    protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext context) {
        String pattern = str(args, "pattern");
        if (pattern.isBlank()) {
            return fail("pattern 不能为空", "MISSING_PATTERN");
        }
        try {
            Path base = Path.of(str(args, "path"));
            if (str(args, "path").isBlank()) {
                base = Path.of(context.cwd());
            }
            if (!base.isAbsolute()) {
                base = Path.of(context.cwd()).resolve(base).normalize();
            }
            final Path root = base;
            if (!Files.isDirectory(root)) {
                return fail("目录不存在: " + root, "DIR_NOT_FOUND");
            }
            // 平台无关匹配：相对路径统一为 '/' 后按 glob→regex 匹配
            java.util.regex.Pattern regex = globToRegex(pattern);
            List<String> hits = new ArrayList<>();
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                        .map(p -> root.relativize(p).toString().replace('\\', '/'))
                        .filter(rel -> regex.matcher(rel).matches())
                        .limit(MAX_RESULTS)
                        .forEach(hits::add);
            }
            return ok(hits.isEmpty() ? "（无匹配）" : String.join("\n", hits));
        } catch (Exception e) {
            return fail("搜索失败: " + e.getMessage(), "SEARCH_ERROR");
        }
    }

    /** glob → 正则（** 跨目录、* 不跨 /、? 单字符）。 */
    static java.util.regex.Pattern globToRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                    regex.append(".*");
                    i++;
                } else {
                    regex.append("[^/]*");
                }
            } else if (c == '?') {
                regex.append("[^/]");
            } else if ("\\.[]{}()+-^$|".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        return java.util.regex.Pattern.compile(regex.toString());
    }
}

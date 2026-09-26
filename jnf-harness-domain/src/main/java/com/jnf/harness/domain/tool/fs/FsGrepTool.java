package com.jnf.harness.domain.tool.fs;

import com.jnf.harness.model.AbstractTool;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 文件内容正则搜索（grep）：命中 文件:行号:内容。
 */
public class FsGrepTool extends AbstractTool {

    private static final int MAX_HITS = 100;

    @Override
    public String name() {
        return "fs_grep";
    }

    @Override
    public String description() {
        return "用正则在文件内容中搜索，返回 文件:行号:内容（最多 100 条命中）。";
    }

    @Override
    public Map<String, Object> parameters() {
        return objectSchema()
                .prop("pattern", stringSchema("正则表达式"))
                .prop("path", stringSchema("搜索根目录（可选，默认工作目录）"))
                .prop("glob", stringSchema("文件名过滤 glob（可选，如 *.java）"))
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
            Pattern regex = Pattern.compile(pattern);
            Path root = str(args, "path").isBlank()
                    ? Path.of(context.cwd()) : Path.of(context.cwd()).resolve(str(args, "path")).normalize();
            if (!Files.isDirectory(root)) {
                return fail("目录不存在: " + root, "DIR_NOT_FOUND");
            }
            String glob = str(args, "glob");
            List<String> hits = new ArrayList<>();
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> glob.isBlank() || p.getFileName().toString().matches(
                                glob.replace(".", "\\.").replace("*", ".*")))
                        .forEach(file -> {
                            if (hits.size() >= MAX_HITS) {
                                return;
                            }
                            try {
                                List<String> lines = Files.readAllLines(file);
                                for (int i = 0; i < lines.size() && hits.size() < MAX_HITS; i++) {
                                    if (regex.matcher(lines.get(i)).find()) {
                                        hits.add(root.relativize(file).toString().replace('\\', '/')
                                                + ":" + (i + 1) + ":" + lines.get(i).trim());
                                    }
                                }
                            } catch (Exception ignored) {
                                // 跳过不可读文件（二进制等）
                            }
                        });
            }
            return ok(hits.isEmpty() ? "（无命中）" : String.join("\n", hits));
        } catch (Exception e) {
            return fail("搜索失败: " + e.getMessage(), "GREP_ERROR");
        }
    }
}

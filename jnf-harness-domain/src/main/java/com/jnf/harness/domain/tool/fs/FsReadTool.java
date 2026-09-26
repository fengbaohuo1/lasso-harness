package com.jnf.harness.domain.tool.fs;

import com.jnf.harness.model.AbstractTool;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 读取文件（行号 + 行数上限，防超大输出挤爆上下文）。
 */
public class FsReadTool extends AbstractTool {

    private static final int MAX_LINES = 2000;

    @Override
    public String name() {
        return "fs_read";
    }

    @Override
    public String description() {
        return "读取文件内容。返回带行号的内容；超过 2000 行自动截断。";
    }

    @Override
    public Map<String, Object> parameters() {
        return objectSchema()
                .prop("path", stringSchema("文件路径（相对工作目录或绝对路径）"))
                .prop("offset", stringSchema("起始行号（从 1 开始，可选）"))
                .prop("limit", stringSchema("读取行数（可选，默认 2000）"))
                .required("path")
                .build();
    }

    @Override
    protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext context) {
        String path = str(args, "path");
        if (path.isBlank()) {
            return fail("path 不能为空", "MISSING_PATH");
        }
        try {
            Path file = Path.of(path);
            if (!file.isAbsolute()) {
                file = Path.of(context.cwd()).resolve(path).normalize();
            }
            if (!Files.exists(file)) {
                return fail("文件不存在: " + file, "FILE_NOT_FOUND");
            }
            if (Files.isDirectory(file)) {
                return fail("是目录不是文件: " + file, "IS_DIRECTORY");
            }
            List<String> lines = Files.readAllLines(file);
            int offset = parseInt(str(args, "offset"), 1);
            int limit = Math.min(parseInt(str(args, "limit"), MAX_LINES), MAX_LINES);
            StringBuilder sb = new StringBuilder();
            int from = Math.max(0, offset - 1);
            int to = Math.min(lines.size(), from + limit);
            for (int i = from; i < to; i++) {
                sb.append(i + 1).append('\t').append(lines.get(i)).append('\n');
            }
            if (to < lines.size()) {
                sb.append("...（共 ").append(lines.size()).append(" 行，已截断）");
            }
            return ok(sb.toString());
        } catch (Exception e) {
            return fail("读取失败: " + e.getMessage(), "READ_ERROR");
        }
    }

    private int parseInt(String raw, int defaultValue) {
        try {
            return raw.isBlank() ? defaultValue : Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}

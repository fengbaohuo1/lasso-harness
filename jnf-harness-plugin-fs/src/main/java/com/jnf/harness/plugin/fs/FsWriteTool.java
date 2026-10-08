package com.jnf.harness.plugin.fs;

import com.jnf.harness.model.AbstractTool;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 写入文件（自动建父目录，覆盖写）。
 */
public class FsWriteTool extends AbstractTool {

    @Override
    public String name() {
        return "fs_write";
    }

    @Override
    public String description() {
        return "写入（覆盖）文件内容，自动创建父目录。";
    }

    @Override
    public Map<String, Object> parameters() {
        return objectSchema()
                .prop("path", stringSchema("文件路径"))
                .prop("content", stringSchema("文件内容"))
                .required("path", "content")
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
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            String content = args.get("content") == null ? "" : String.valueOf(args.get("content"));
            Files.writeString(file, content);
            return ok("已写入 " + file + "（" + content.length() + " 字符）");
        } catch (Exception e) {
            return fail("写入失败: " + e.getMessage(), "WRITE_ERROR");
        }
    }
}

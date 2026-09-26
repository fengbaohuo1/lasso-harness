package com.jnf.harness.domain.tool.fs;

import com.jnf.harness.model.AbstractTool;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 读取图片（返回 data URI，供后续图片块管线使用）。
 * <p>过渡语义：当前 LLM 适配器仅映射文本内容，data URI 以文本形式回流；
 * 图片块直传挂账到 LLM 管线扩展（见 plan 进度）。
 */
public class FsReadImageTool extends AbstractTool {

    private static final long MAX_BYTES = 2 * 1024 * 1024;

    @Override
    public String name() {
        return "fs_read_image";
    }

    @Override
    public String description() {
        return "读取图片文件，返回 data URI（支持 png/jpg/gif/webp，≤2MB）。";
    }

    @Override
    public Map<String, Object> parameters() {
        return objectSchema()
                .prop("path", stringSchema("图片文件路径"))
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
            if (Files.size(file) > MAX_BYTES) {
                return fail("图片超过 2MB 上限", "IMAGE_TOO_LARGE");
            }
            String mediaType = mediaTypeOf(file.getFileName().toString());
            if (mediaType == null) {
                return fail("不支持的图片类型（png/jpg/jpeg/gif/webp）", "UNSUPPORTED_IMAGE");
            }
            String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(file));
            return ok("data:" + mediaType + ";base64," + base64);
        } catch (Exception e) {
            return fail("读取失败: " + e.getMessage(), "READ_ERROR");
        }
    }

    private String mediaTypeOf(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return null;
    }
}

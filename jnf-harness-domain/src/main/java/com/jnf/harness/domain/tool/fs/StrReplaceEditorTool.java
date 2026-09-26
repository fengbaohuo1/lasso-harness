package com.jnf.harness.domain.tool.fs;

import com.jnf.harness.model.AbstractTool;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 精确字符串替换编辑（old_str 必须唯一命中，避免误改）。
 */
public class StrReplaceEditorTool extends AbstractTool {

    @Override
    public String name() {
        return "str_replace_editor";
    }

    @Override
    public String description() {
        return "在文件中做一次精确字符串替换：old_str 必须在文件中唯一出现，替换为 new_str。";
    }

    @Override
    public Map<String, Object> parameters() {
        return objectSchema()
                .prop("path", stringSchema("文件路径"))
                .prop("old_str", stringSchema("被替换的原文（必须唯一）"))
                .prop("new_str", stringSchema("替换后的内容"))
                .required("path", "old_str", "new_str")
                .build();
    }

    @Override
    protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext context) {
        String path = str(args, "path");
        String oldStr = str(args, "old_str");
        String newStr = args.get("new_str") == null ? "" : String.valueOf(args.get("new_str"));
        if (path.isBlank() || oldStr.isBlank()) {
            return fail("path/old_str 不能为空", "MISSING_ARGUMENT");
        }
        try {
            Path file = Path.of(path);
            if (!file.isAbsolute()) {
                file = Path.of(context.cwd()).resolve(path).normalize();
            }
            if (!Files.exists(file)) {
                return fail("文件不存在: " + file, "FILE_NOT_FOUND");
            }
            String content = Files.readString(file);
            int first = content.indexOf(oldStr);
            int last = content.lastIndexOf(oldStr);
            if (first < 0) {
                return fail("old_str 未在文件中找到", "STR_NOT_FOUND");
            }
            if (first != last) {
                return fail("old_str 命中多处（" + (count(content, oldStr)) + " 次），请提供更长的唯一上下文",
                        "STR_NOT_UNIQUE");
            }
            Files.writeString(file, content.substring(0, first) + newStr + content.substring(first + oldStr.length()));
            return ok("替换成功: " + file);
        } catch (Exception e) {
            return fail("替换失败: " + e.getMessage(), "EDIT_ERROR");
        }
    }

    private int count(String content, String needle) {
        int count = 0;
        int index = 0;
        while ((index = content.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}

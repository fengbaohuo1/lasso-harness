package com.jnf.harness.plugin.fs;

import com.jnf.harness.model.ToolDefinition;
import com.jnf.harness.spi.AbstractHarnessPlugin;
import com.jnf.harness.spi.PluginContext;
import com.jnf.harness.spi.host.PluginHttpResponse;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 官方文件工具插件入口（P2）：内核零内置工具，文件族以插件 JAR 交付。
 * <p>plugin.yaml 声明 {@code toolNamespace: global}——工具身份不随承载形态变化
 * （fs_read 等全局名保持稳定，宿主保证全局唯一）。
 * <p>C2：@file 提及源（mentionProvider）由本插件交付——list-files（候选）/read-file（展开）
 * 两端点 + plugin-ui.json 声明，桌面端贡献运行时解释。
 */
public class FsToolsPlugin extends AbstractHarnessPlugin {

    public FsToolsPlugin() {
        super("builtin-fs");
    }

    @Override
    public List<ToolDefinition> tools() {
        return List.of(
                new FsReadTool(),
                new FsWriteTool(),
                new FsSearchTool(),
                new FsGrepTool(),
                new FsReadImageTool(),
                new StrReplaceEditorTool());
    }

    @Override
    public void configure(PluginContext context) {
        super.configure(context);
        // @file 候选：GET /api/harness/plugins/builtin-fs/api/list-files?q=&cwd=
        context.registerEndpoint("GET", "list-files", request -> {
            Path root = rootOf(request.query().get("cwd"));
            List<Map<String, Object>> items = FsFileIndex.list(
                    root, request.query().get("q"), FsFileIndex.MAX_RESULTS);
            return PluginHttpResponse.ok(Map.of("items", items));
        });
        // @file 展开：GET /api/harness/plugins/builtin-fs/api/read-file?path=&cwd=
        context.registerEndpoint("GET", "read-file", request -> {
            Path root = rootOf(request.query().get("cwd"));
            Map<String, Object> result = FsFileIndex.read(
                    root, request.query().get("path"), FsFileIndex.MAX_READ_CHARS);
            return result == null
                    ? PluginHttpResponse.error(400, "文件不可读或越界: " + request.query().get("path"))
                    : PluginHttpResponse.ok(result);
        });
    }

    /** 候选根目录：cwd 参数优先，缺省进程工作目录。 */
    private static Path rootOf(String cwd) {
        return cwd == null || cwd.isBlank()
                ? Path.of("").toAbsolutePath()
                : Path.of(cwd).toAbsolutePath().normalize();
    }
}

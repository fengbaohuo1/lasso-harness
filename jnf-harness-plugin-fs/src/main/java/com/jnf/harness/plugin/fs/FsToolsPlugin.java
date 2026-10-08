package com.jnf.harness.plugin.fs;

import com.jnf.harness.model.ToolDefinition;
import com.jnf.harness.spi.AbstractHarnessPlugin;

import java.util.List;

/**
 * 官方文件工具插件入口（P2）：内核零内置工具，文件族以插件 JAR 交付。
 * <p>plugin.yaml 声明 {@code toolNamespace: global}——工具身份不随承载形态变化
 * （fs_read 等全局名保持稳定，宿主保证全局唯一）。
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
}

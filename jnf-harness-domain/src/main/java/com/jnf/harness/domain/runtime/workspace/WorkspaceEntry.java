package com.jnf.harness.domain.runtime.workspace;

/**
 * 工作区条目（P9-1）：一个可绑定到会话的根目录。
 *
 * @param workspaceCode 工作区码（default=内置默认工作区，不可删）
 * @param displayName   展示名
 * @param rootPath      根目录绝对路径（工具 cwd、沙箱工作区根）
 */
public record WorkspaceEntry(String workspaceCode, String displayName, String rootPath) {

    public static final String DEFAULT_CODE = "default";
    public static final String DEFAULT_NAME = "默认工作区";
}

package com.jnf.harness.infrastructure.dao.po;

/**
 * harness_workspace 行对象（P9-1 工作区注册表）。
 */
public class WorkspacePO {

    private String workspaceCode;
    private String displayName;
    private String rootPath;

    public String getWorkspaceCode() {
        return workspaceCode;
    }

    public void setWorkspaceCode(String workspaceCode) {
        this.workspaceCode = workspaceCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }
}

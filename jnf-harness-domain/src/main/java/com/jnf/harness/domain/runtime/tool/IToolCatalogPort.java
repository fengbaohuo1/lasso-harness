package com.jnf.harness.domain.runtime.tool;

import java.util.List;

/**
 * 工具目录端口（R5 真接线：目录治理来自数据库，不再只信代码注册表）。
 */
public interface IToolCatalogPort {

    /** 某画像（profile）启用的工具目录。 */
    List<ToolCatalogEntry> findEnabled(String profileCode);

    List<ToolCatalogEntry> findAll();

    /** 目录条目。 */
    record ToolCatalogEntry(String toolCode, String displayName, String description,
                            String category, boolean requiresApproval) {
    }
}

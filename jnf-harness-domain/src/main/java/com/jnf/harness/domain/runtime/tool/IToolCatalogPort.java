package com.jnf.harness.domain.runtime.tool;

import java.util.List;

/**
 * 工具目录端口（R5 真接线：目录治理来自数据库，不再只信代码注册表）。
 */
public interface IToolCatalogPort {

    /** 某画像（profile）启用的工具目录。 */
    List<ToolCatalogEntry> findEnabled(String profileCode);

    List<ToolCatalogEntry> findAll();

    /**
     * 幂等登记工具并绑定画像（插件激活自动上架，P2/P3）。
     * <p>已存在的条目保留管理员配置（不覆盖 requiresApproval 等）；仅补缺条目与画像绑定。
     */
    void upsert(ToolCatalogEntry entry, String profileCode);

    /** 整体替换某画像的工具绑定（A1 画像白名单管理；toolCodes 为空 = 清空绑定，回落全量语义）。 */
    void replaceBindings(String profileCode, List<String> toolCodes);

    /** 目录条目。 */
    record ToolCatalogEntry(String toolCode, String displayName, String description,
                            String category, boolean requiresApproval) {
    }
}

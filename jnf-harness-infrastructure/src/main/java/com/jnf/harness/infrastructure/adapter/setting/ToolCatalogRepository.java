package com.jnf.harness.infrastructure.adapter.setting;

import com.jnf.harness.domain.runtime.tool.IToolCatalogPort;
import com.jnf.harness.infrastructure.dao.ToolCatalogDao;
import com.jnf.harness.infrastructure.dao.po.ToolCatalogPO;

import java.util.List;

/**
 * 工具目录仓储（MyBatis 实现，R5）。
 */
public class ToolCatalogRepository implements IToolCatalogPort {

    private final ToolCatalogDao dao;

    public ToolCatalogRepository(ToolCatalogDao dao) {
        this.dao = dao;
    }

    @Override
    public List<ToolCatalogEntry> findEnabled(String profileCode) {
        return dao.findEnabled(profileCode).stream().map(ToolCatalogRepository::toEntry).toList();
    }

    @Override
    public List<ToolCatalogEntry> findAll() {
        return dao.findAll().stream().map(ToolCatalogRepository::toEntry).toList();
    }

    @Override
    public void upsert(ToolCatalogEntry entry, String profileCode) {
        dao.insertIfAbsent(entry.toolCode(),
                entry.displayName() == null ? entry.toolCode() : entry.displayName(),
                entry.description(), entry.category(), entry.requiresApproval());
        dao.bindIfAbsent(profileCode, entry.toolCode());
    }

    @Override
    public void replaceBindings(String profileCode, java.util.List<String> toolCodes) {
        dao.deleteBindings(profileCode);
        if (toolCodes != null) {
            toolCodes.stream().filter(code -> code != null && !code.isBlank())
                    .forEach(code -> dao.bindIfAbsent(profileCode, code.trim()));
        }
    }

    private static ToolCatalogEntry toEntry(ToolCatalogPO po) {
        return new ToolCatalogEntry(po.getToolCode(), po.getDisplayName(),
                po.getDescription(), po.getCategory(), po.isRequiresApproval());
    }
}

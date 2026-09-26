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

    private static ToolCatalogEntry toEntry(ToolCatalogPO po) {
        return new ToolCatalogEntry(po.getToolCode(), po.getDisplayName(),
                po.getDescription(), po.getCategory(), po.isRequiresApproval());
    }
}

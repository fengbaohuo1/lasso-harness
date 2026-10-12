package com.jnf.harness.infrastructure.adapter.setting;

import com.jnf.harness.domain.runtime.workspace.IWorkspaceRepository;
import com.jnf.harness.domain.runtime.workspace.WorkspaceEntry;
import com.jnf.harness.infrastructure.dao.WorkspaceDao;
import com.jnf.harness.infrastructure.dao.po.WorkspacePO;

import java.util.List;
import java.util.Optional;

/**
 * 工作区仓储（MyBatis 实现，P9-1）：save = update 未命中则 insert（H2/MySQL 双兼容）。
 */
public class WorkspaceRepository implements IWorkspaceRepository {

    private final WorkspaceDao dao;

    public WorkspaceRepository(WorkspaceDao dao) {
        this.dao = dao;
    }

    @Override
    public List<WorkspaceEntry> findAll() {
        return dao.findAll().stream().map(WorkspaceRepository::toRecord).toList();
    }

    @Override
    public Optional<WorkspaceEntry> find(String workspaceCode) {
        return Optional.ofNullable(dao.findByCode(workspaceCode)).map(WorkspaceRepository::toRecord);
    }

    @Override
    public void save(WorkspaceEntry entry) {
        WorkspacePO po = toPo(entry);
        if (dao.update(po) == 0) {
            dao.insert(po);
        }
    }

    @Override
    public void delete(String workspaceCode) {
        dao.delete(workspaceCode);
    }

    private static WorkspaceEntry toRecord(WorkspacePO po) {
        return new WorkspaceEntry(po.getWorkspaceCode(), po.getDisplayName(), po.getRootPath());
    }

    private static WorkspacePO toPo(WorkspaceEntry entry) {
        WorkspacePO po = new WorkspacePO();
        po.setWorkspaceCode(entry.workspaceCode());
        po.setDisplayName(entry.displayName());
        po.setRootPath(entry.rootPath());
        return po;
    }
}

package com.jnf.harness.domain.runtime.workspace;

import java.util.List;
import java.util.Optional;

/**
 * 工作区仓储缝（P9-1）。
 */
public interface IWorkspaceRepository {

    List<WorkspaceEntry> findAll();

    Optional<WorkspaceEntry> find(String workspaceCode);

    void save(WorkspaceEntry entry);

    void delete(String workspaceCode);
}

package com.jnf.harness.domain.runtime.workspace;

import com.jnf.harness.domain.session.meta.SessionMeta;
import com.jnf.harness.domain.session.meta.SessionMetaService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 工作区服务（P9-1）：注册表（增删改查）+ 会话绑定解析（cwd = 会话工作区根）。
 * <p>default 工作区永在（缺省=进程工作目录），不可删除；会话绑定的工作区失效时回落 default。
 */
public class WorkspaceService {

    private final IWorkspaceRepository repository;
    private final SessionMetaService sessionMetaService;

    public WorkspaceService(IWorkspaceRepository repository, SessionMetaService sessionMetaService) {
        this.repository = repository;
        this.sessionMetaService = sessionMetaService;
    }

    /** 列出全部工作区（保证 default 在列）。 */
    public List<WorkspaceEntry> list() {
        ensureDefault();
        List<WorkspaceEntry> all = new ArrayList<>(repository.findAll());
        all.sort((a, b) -> {
            if (WorkspaceEntry.DEFAULT_CODE.equals(a.workspaceCode())) {
                return -1;
            }
            if (WorkspaceEntry.DEFAULT_CODE.equals(b.workspaceCode())) {
                return 1;
            }
            return a.workspaceCode().compareTo(b.workspaceCode());
        });
        return all;
    }

    public Optional<WorkspaceEntry> find(String workspaceCode) {
        ensureDefault();
        return repository.find(workspaceCode == null ? "" : workspaceCode.trim());
    }

    /** 新建工作区：rootPath 必填，目录不存在则创建；码自动生成。 */
    public WorkspaceEntry create(String displayName, String rootPath) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName 必填");
        }
        Path root = normalizeRoot(rootPath);
        String code = "w-" + Long.toString(System.currentTimeMillis(), 36);
        WorkspaceEntry entry = new WorkspaceEntry(code, displayName.trim(), root.toString());
        repository.save(entry);
        return entry;
    }

    /** 更新工作区（default 的 rootPath 允许改——它就是「当前工作区」的可调形态）。 */
    public WorkspaceEntry update(String workspaceCode, String displayName, String rootPath) {
        WorkspaceEntry existing = find(workspaceCode)
                .orElseThrow(() -> new IllegalArgumentException("工作区不存在: " + workspaceCode));
        String name = displayName == null || displayName.isBlank() ? existing.displayName() : displayName.trim();
        String root = rootPath == null || rootPath.isBlank() ? existing.rootPath() : normalizeRoot(rootPath).toString();
        WorkspaceEntry updated = new WorkspaceEntry(existing.workspaceCode(), name, root);
        repository.save(updated);
        return updated;
    }

    /** 删除工作区（default 不可删）。 */
    public void delete(String workspaceCode) {
        String code = workspaceCode == null ? "" : workspaceCode.trim();
        if (WorkspaceEntry.DEFAULT_CODE.equals(code)) {
            throw new IllegalStateException("默认工作区不可删除");
        }
        if (repository.find(code).isEmpty()) {
            throw new IllegalArgumentException("工作区不存在: " + code);
        }
        repository.delete(code);
    }

    /**
     * 会话的工作区根（P9-1：工具 cwd、沙箱工作区根）：
     * 会话绑定 → default 工作区 → 进程目录兜底。
     */
    public String cwdFor(String sessionId) {
        ensureDefault();
        if (sessionId != null && !sessionId.isBlank()) {
            Optional<SessionMeta> meta = sessionMetaService.find(sessionId.trim());
            if (meta.isPresent() && meta.get().workspaceCode() != null) {
                Optional<WorkspaceEntry> bound = repository.find(meta.get().workspaceCode());
                if (bound.isPresent()) {
                    return bound.get().rootPath();
                }
            }
        }
        return repository.find(WorkspaceEntry.DEFAULT_CODE)
                .map(WorkspaceEntry::rootPath)
                .orElseGet(() -> Path.of("").toAbsolutePath().toString());
    }

    private void ensureDefault() {
        if (repository.find(WorkspaceEntry.DEFAULT_CODE).isEmpty()) {
            repository.save(new WorkspaceEntry(WorkspaceEntry.DEFAULT_CODE, WorkspaceEntry.DEFAULT_NAME,
                    Path.of("").toAbsolutePath().toString()));
        }
    }

    private static Path normalizeRoot(String rootPath) {
        if (rootPath == null || rootPath.isBlank()) {
            throw new IllegalArgumentException("rootPath 必填");
        }
        Path root = Path.of(rootPath.trim()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (Exception e) {
            throw new IllegalArgumentException("工作区根目录不可创建: " + root + "（" + e.getMessage() + "）");
        }
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("工作区根目录不是目录: " + root);
        }
        return root;
    }
}

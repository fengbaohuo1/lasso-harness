package com.jnf.harness.domain.runtime.workspace;

import com.jnf.harness.domain.session.meta.SessionMeta;
import com.jnf.harness.domain.session.meta.SessionMetaService;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 工作区服务（P9-1 + P10-6）：注册表（增删改查）+ 会话绑定解析（cwd = 会话工作区根）+ 目录浏览。
 * <p>default 工作区永在（缺省=进程工作目录），不可删除；会话绑定的工作区失效时回落 default；
 * 删除工作区时**解绑**其下会话，避免残留指向已删工作区的悬空码（会话分组出现幽灵工作区）。
 */
public class WorkspaceService {

    /** 目录浏览单页上限（防深层目录一次拉爆 UI）。 */
    public static final int BROWSE_LIMIT = 500;

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

    /**
     * 删除工作区（default 不可删）：连带解绑其下会话（回到 default 工作区）。
     *
     * @return 被解绑的会话数（供调用方提示「N 个会话已回到默认工作区」）
     */
    public int delete(String workspaceCode) {
        String code = workspaceCode == null ? "" : workspaceCode.trim();
        if (WorkspaceEntry.DEFAULT_CODE.equals(code)) {
            throw new IllegalStateException("默认工作区不可删除");
        }
        if (repository.find(code).isEmpty()) {
            throw new IllegalArgumentException("工作区不存在: " + code);
        }
        int unbound = sessionMetaService.clearWorkspace(code);
        repository.delete(code);
        return unbound;
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

    /**
     * 目录浏览（P10-5：新建工作区时挑目录，免手打绝对路径）。
     * <p>只列子目录（不列文件）；路径不存在/不可读不抛异常，返回 {@code error} 让 UI 提示；
     * 空路径起点 = 用户主目录（无则 default 工作区根）。
     */
    public DirectoryListing browse(String path) {
        List<String> roots = driveRoots();
        Path start = resolveStart(path);
        if (start == null) {
            return new DirectoryListing("", null, roots, List.of(), "无法确定起始目录");
        }
        Path dir = Files.isDirectory(start) ? start : start.getParent();
        if (dir == null || !Files.isDirectory(dir)) {
            return new DirectoryListing(start.toString(), null, roots, List.of(), "目录不存在");
        }
        List<DirectoryListing.Child> children = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isDirectory)
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .limit(BROWSE_LIMIT)
                    .forEach(p -> children.add(new DirectoryListing.Child(p.getFileName().toString(),
                            p.toAbsolutePath().normalize().toString())));
            return new DirectoryListing(dir.toAbsolutePath().normalize().toString(),
                    dir.getParent() == null ? null : dir.getParent().toString(), roots, children, null);
        } catch (Exception e) {
            return new DirectoryListing(dir.toAbsolutePath().normalize().toString(), null, roots, List.of(),
                    "目录不可读：" + e.getMessage());
        }
    }

    private Path resolveStart(String path) {
        if (path != null && !path.isBlank()) {
            try {
                return Path.of(path.trim()).toAbsolutePath().normalize();
            } catch (Exception e) {
                return null;
            }
        }
        String home = System.getProperty("user.home");
        if (home != null && !home.isBlank()) {
            return Path.of(home).toAbsolutePath().normalize();
        }
        return repository.find(WorkspaceEntry.DEFAULT_CODE)
                .map(entry -> Path.of(entry.rootPath()))
                .orElse(null);
    }

    /** 盘符/根列表（Windows 给 C:\、D:\…，类 Unix 给 /）——UI 的快速跳转按钮。 */
    private static List<String> driveRoots() {
        File[] roots = File.listRoots();
        if (roots == null || roots.length == 0) {
            return List.of(Path.of("/").toString());
        }
        List<String> out = new ArrayList<>(roots.length);
        for (File root : roots) {
            out.add(root.getAbsolutePath());
        }
        return out;
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

    /**
     * 目录浏览结果（P10-5）。
     *
     * @param path        当前目录绝对路径
     * @param parent      上级目录（盘根为 null）
     * @param roots       可用盘符/根（UI 快速跳转）
     * @param directories 子目录（按名升序，上限 {@link #BROWSE_LIMIT}）
     * @param error       非致命错误文案（null = 正常）
     */
    public record DirectoryListing(String path, String parent, List<String> roots,
                                   List<Child> directories, String error) {

        /** 子目录条目。 */
        public record Child(String name, String path) {
        }
    }
}

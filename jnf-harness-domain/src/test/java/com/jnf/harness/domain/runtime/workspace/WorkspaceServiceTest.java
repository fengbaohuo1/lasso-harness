package com.jnf.harness.domain.runtime.workspace;

import com.jnf.harness.domain.session.meta.ISessionMetaStore;
import com.jnf.harness.domain.session.meta.SessionMeta;
import com.jnf.harness.domain.session.meta.SessionMetaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工作区服务验收（P9-1）：注册表 CRUD + default 保护 + 会话绑定解析。
 */
class WorkspaceServiceTest {

    private final Map<String, WorkspaceEntry> table = new LinkedHashMap<>();
    private final Map<String, SessionMeta> metaTable = new LinkedHashMap<>();

    private final WorkspaceService service = new WorkspaceService(new IWorkspaceRepository() {
        @Override
        public List<WorkspaceEntry> findAll() {
            return List.copyOf(table.values());
        }

        @Override
        public Optional<WorkspaceEntry> find(String workspaceCode) {
            return Optional.ofNullable(table.get(workspaceCode));
        }

        @Override
        public void save(WorkspaceEntry entry) {
            table.put(entry.workspaceCode(), entry);
        }

        @Override
        public void delete(String workspaceCode) {
            table.remove(workspaceCode);
        }
    }, new SessionMetaService(new ISessionMetaStore() {
        @Override
        public Optional<SessionMeta> find(String sessionId) {
            return Optional.ofNullable(metaTable.get(sessionId));
        }

        @Override
        public List<SessionMeta> findAll() {
            return List.copyOf(metaTable.values());
        }

        @Override
        public void save(SessionMeta meta) {
            metaTable.put(meta.sessionId(), meta);
        }
    }));

    @Test
    @DisplayName("default 工作区永在且不可删")
    void defaultProtected() {
        assertTrue(service.list().stream().anyMatch(w -> "default".equals(w.workspaceCode())));
        assertThrows(IllegalStateException.class, () -> service.delete("default"));
    }

    @Test
    @DisplayName("新建/改名/删除 + 会话绑定解析与回落")
    void crudAndBinding() {
        WorkspaceEntry created = service.create("测试工作区", "target/ws-test-p9");
        assertTrue(created.workspaceCode().startsWith("w-"));
        assertTrue(service.find(created.workspaceCode()).isPresent());

        // 未绑定会话 → default 根
        String defaultRoot = service.cwdFor("no-such-session");
        assertTrue(defaultRoot != null && !defaultRoot.isBlank());

        // 绑定后 → 工作区根
        metaTable.put("s-1", new SessionMeta("s-1", null, false, System.currentTimeMillis(),
                created.workspaceCode(), null, null));
        assertEquals(created.rootPath(), service.cwdFor("s-1"));

        // 绑定的工作区被删 → 回落 default，且会话 meta 被解绑（P10-6：不留悬空码）
        assertEquals(1, service.delete(created.workspaceCode()), "删除工作区应解绑 1 个会话");
        assertEquals(defaultRoot, service.cwdFor("s-1"));
        assertEquals(null, metaTable.get("s-1").workspaceCode(), "会话不应残留已删工作区的码");

        // 更新改名
        WorkspaceEntry updated = service.update("default", "主工作区", null);
        assertEquals("主工作区", updated.displayName());
    }

    @Test
    @DisplayName("目录浏览（P10-5）：列子目录、不可读路径不抛异常、盘根列表非空")
    void browseDirectories() {
        WorkspaceService.DirectoryListing listing = service.browse("target");
        assertTrue(listing.error() == null, "target 目录应可读: " + listing.error());
        assertTrue(listing.path().endsWith("target"));
        assertTrue(listing.roots() != null && !listing.roots().isEmpty());

        // 路径不存在但父目录在：回落到最近的已存在目录（用户手打半截路径时的友好行为）
        WorkspaceService.DirectoryListing fallback = service.browse("target/no-such-child");
        assertTrue(fallback.error() == null);
        assertTrue(fallback.path().endsWith("target"));

        // 父目录也不存在：不抛异常，error 有文案
        WorkspaceService.DirectoryListing missing = service.browse("no-such-dir-xyz/deeper/leaf");
        assertEquals("目录不存在", missing.error());

        // 空路径：从用户主目录起
        WorkspaceService.DirectoryListing home = service.browse(null);
        assertTrue(home.path() != null && !home.path().isBlank());
    }

    @Test
    @DisplayName("非法输入收敛为 IllegalArgumentException")
    void invalidInput() {
        assertThrows(IllegalArgumentException.class, () -> service.create("", "target/x"));
        assertThrows(IllegalArgumentException.class, () -> service.create("缺根目录", " "));
        assertThrows(IllegalArgumentException.class, () -> service.update("nope", "名", null));
    }
}

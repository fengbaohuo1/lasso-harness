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

        // 绑定的工作区被删 → 回落 default
        service.delete(created.workspaceCode());
        assertEquals(defaultRoot, service.cwdFor("s-1"));

        // 更新改名
        WorkspaceEntry updated = service.update("default", "主工作区", null);
        assertEquals("主工作区", updated.displayName());
    }

    @Test
    @DisplayName("非法输入收敛为 IllegalArgumentException")
    void invalidInput() {
        assertThrows(IllegalArgumentException.class, () -> service.create("", "target/x"));
        assertThrows(IllegalArgumentException.class, () -> service.create("缺根目录", " "));
        assertThrows(IllegalArgumentException.class, () -> service.update("nope", "名", null));
    }
}

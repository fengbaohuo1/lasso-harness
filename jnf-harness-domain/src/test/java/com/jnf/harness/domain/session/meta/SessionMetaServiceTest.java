package com.jnf.harness.domain.session.meta;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F3 会话元数据：重命名校验 / 软删语义 / 已删会话禁重命名。
 */
class SessionMetaServiceTest {

    /** 内存存储桩。 */
    private static final class InMemoryStore implements ISessionMetaStore {
        private final Map<String, SessionMeta> metas = new HashMap<>();

        @Override
        public Optional<SessionMeta> find(String sessionId) {
            return Optional.ofNullable(metas.get(sessionId));
        }

        @Override
        public java.util.List<SessionMeta> findAll() {
            return java.util.List.copyOf(metas.values());
        }

        @Override
        public void save(SessionMeta meta) {
            metas.put(meta.sessionId(), meta);
        }
    }

    @Test
    @DisplayName("重命名写入标题；再查可得")
    void rename_saves_title() {
        SessionMetaService service = new SessionMetaService(new InMemoryStore());
        SessionMeta meta = service.rename("s-1", "  改造计划  ");
        assertEquals("改造计划", meta.title());
        assertFalse(meta.deleted());
        assertEquals("改造计划", service.find("s-1").orElseThrow().title());
    }

    @Test
    @DisplayName("空标题与超长标题拒绝")
    void rename_validates_title() {
        SessionMetaService service = new SessionMetaService(new InMemoryStore());
        assertThrows(IllegalArgumentException.class, () -> service.rename("s-1", "   "));
        assertThrows(IllegalArgumentException.class, () -> service.rename("s-1", null));
        assertThrows(IllegalArgumentException.class,
                () -> service.rename("s-1", "x".repeat(SessionMetaService.MAX_TITLE_LENGTH + 1)));
    }

    @Test
    @DisplayName("软删置 deleted=true；已删会话拒绝重命名")
    void softDelete_marks_and_blocks_rename() {
        SessionMetaService service = new SessionMetaService(new InMemoryStore());
        service.rename("s-1", "旧标题");
        SessionMeta deleted = service.softDelete("s-1");
        assertTrue(deleted.deleted());
        assertEquals("旧标题", deleted.title(), "软删保留标题");
        assertThrows(IllegalStateException.class, () -> service.rename("s-1", "新标题"));
    }

    @Test
    @DisplayName("P10-6：按工作区解绑会话——只清匹配项，预设/标题/思考强度保留")
    void clearWorkspace_unbinds_only_matching() {
        InMemoryStore store = new InMemoryStore();
        SessionMetaService service = new SessionMetaService(store);
        store.save(new SessionMeta("s-1", "甲", false, 1L, "w-a", "deep", "high"));
        store.save(new SessionMeta("s-2", "乙", false, 1L, "w-b", null, null));
        store.save(new SessionMeta("s-3", null, false, 1L, "w-a", null, null));

        assertEquals(2, service.clearWorkspace("w-a"));

        SessionMeta s1 = service.find("s-1").orElseThrow();
        assertEquals(null, s1.workspaceCode());
        assertEquals("甲", s1.title());
        assertEquals("deep", s1.presetCode(), "解绑工作区不应清掉预设");
        assertEquals("high", s1.reasoningEffort());
        assertEquals("w-b", service.find("s-2").orElseThrow().workspaceCode(), "其它工作区不受影响");
        assertEquals(0, service.clearWorkspace(null), "空码是空操作");
        assertEquals(0, service.clearWorkspace("w-none"));
    }
}

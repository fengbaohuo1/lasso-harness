package com.jnf.harness.domain.session.meta;

import java.util.Optional;

/**
 * 会话元数据服务：重命名 / 软删（F3 会话管理）。
 */
public class SessionMetaService {

    /** 标题长度上限（UI 输入同步约束）。 */
    public static final int MAX_TITLE_LENGTH = 200;

    private final ISessionMetaStore store;

    public SessionMetaService(ISessionMetaStore store) {
        this.store = store;
    }

    /** 重命名会话；标题 1~200 字符，已软删会话拒绝重命名。 */
    public SessionMeta rename(String sessionId, String title) {
        String trimmed = title == null ? "" : title.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("标题不能为空");
        }
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("标题最长 " + MAX_TITLE_LENGTH + " 字符");
        }
        SessionMeta meta = store.find(sessionId)
                .orElseGet(() -> new SessionMeta(sessionId, null, false, System.currentTimeMillis()));
        if (meta.deleted()) {
            throw new IllegalStateException("会话已删除，无法重命名");
        }
        SessionMeta updated = new SessionMeta(sessionId, trimmed, false, System.currentTimeMillis());
        store.save(updated);
        return updated;
    }

    /** 软删会话（事件文件保留，列表过滤）。 */
    public SessionMeta softDelete(String sessionId) {
        SessionMeta meta = store.find(sessionId)
                .orElseGet(() -> new SessionMeta(sessionId, null, false, System.currentTimeMillis()));
        SessionMeta updated = new SessionMeta(sessionId, meta.title(), true, System.currentTimeMillis());
        store.save(updated);
        return updated;
    }

    public Optional<SessionMeta> find(String sessionId) {
        return store.find(sessionId);
    }
}

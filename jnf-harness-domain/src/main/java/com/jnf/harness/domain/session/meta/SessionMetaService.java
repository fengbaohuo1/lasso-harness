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
        SessionMeta updated = new SessionMeta(sessionId, trimmed, false, System.currentTimeMillis(),
                meta.workspaceCode(), meta.presetCode(), meta.reasoningEffort());
        store.save(updated);
        return updated;
    }

    /** 软删会话（事件文件保留，列表过滤）。 */
    public SessionMeta softDelete(String sessionId) {
        SessionMeta meta = store.find(sessionId)
                .orElseGet(() -> new SessionMeta(sessionId, null, false, System.currentTimeMillis()));
        SessionMeta updated = new SessionMeta(sessionId, meta.title(), true, System.currentTimeMillis(),
                meta.workspaceCode(), meta.presetCode(), meta.reasoningEffort());
        store.save(updated);
        return updated;
    }

    /**
     * 更新会话设置（P9：工作区/智能体预设/思考强度）——合并语义：null 字段沿用原值。
     * <p>预设码一经绑定不建议中途更换（上下文管理一致性），但不强禁（预览/实验场景）。
     */
    public SessionMeta updateSettings(String sessionId, String workspaceCode, String presetCode,
                                      String reasoningEffort) {
        SessionMeta meta = store.find(sessionId)
                .orElseGet(() -> new SessionMeta(sessionId, null, false, System.currentTimeMillis()));
        if (meta.deleted()) {
            throw new IllegalStateException("会话已删除，无法更新设置");
        }
        SessionMeta updated = new SessionMeta(sessionId, meta.title(), false, System.currentTimeMillis(),
                workspaceCode != null ? blankToNull(workspaceCode) : meta.workspaceCode(),
                presetCode != null ? blankToNull(presetCode) : meta.presetCode(),
                reasoningEffort != null ? blankToNull(reasoningEffort) : meta.reasoningEffort());
        store.save(updated);
        return updated;
    }

    private static String blankToNull(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public Optional<SessionMeta> find(String sessionId) {
        return store.find(sessionId);
    }
}

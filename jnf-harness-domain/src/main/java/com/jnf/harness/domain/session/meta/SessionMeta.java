package com.jnf.harness.domain.session.meta;

/**
 * 会话元数据：标题、软删标记与会话设置（P9）。
 * <p>事件文件（events.jsonl）永远保留可审计；删除只置 {@code deleted=true}，列表过滤展示。
 *
 * @param workspaceCode   会话绑定工作区码（P9-1，可空=默认工作区）
 * @param presetCode      会话绑定智能体预设码（P9-7，可空=default；新会话选定后不变）
 * @param reasoningEffort 会话思考强度（P9-8，off/low/medium/high，可空=预设值）
 */
public record SessionMeta(String sessionId, String title, boolean deleted, long updatedAt,
                          String workspaceCode, String presetCode, String reasoningEffort) {

    public SessionMeta {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId 必填");
        }
        if (reasoningEffort != null) {
            reasoningEffort = reasoningEffort.trim().toLowerCase(java.util.Locale.ROOT);
            reasoningEffort = reasoningEffort.isEmpty() ? null : reasoningEffort;
            if (reasoningEffort != null
                    && !java.util.Set.of("off", "low", "medium", "high").contains(reasoningEffort)) {
                throw new IllegalArgumentException("reasoningEffort 只能是 off/low/medium/high");
            }
        }
    }

    /** 兼容旧构造面（无 P9 会话设置）。 */
    public SessionMeta(String sessionId, String title, boolean deleted, long updatedAt) {
        this(sessionId, title, deleted, updatedAt, null, null, null);
    }
}

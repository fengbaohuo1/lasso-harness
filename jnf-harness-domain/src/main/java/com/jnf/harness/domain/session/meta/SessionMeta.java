package com.jnf.harness.domain.session.meta;

/**
 * 会话元数据：标题与软删标记。
 * <p>事件文件（events.jsonl）永远保留可审计；删除只置 {@code deleted=true}，列表过滤展示。
 */
public record SessionMeta(String sessionId, String title, boolean deleted, long updatedAt) {

    public SessionMeta {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId 必填");
        }
    }
}

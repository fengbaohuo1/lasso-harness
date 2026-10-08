package com.jnf.harness.domain.session.meta;

import java.util.Optional;

/**
 * 会话元数据存储端口。
 */
public interface ISessionMetaStore {

    Optional<SessionMeta> find(String sessionId);

    void save(SessionMeta meta);
}

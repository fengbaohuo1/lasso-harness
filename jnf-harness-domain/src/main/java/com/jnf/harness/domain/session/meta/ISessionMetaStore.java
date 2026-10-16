package com.jnf.harness.domain.session.meta;

import java.util.List;
import java.util.Optional;

/**
 * 会话元数据存储端口。
 */
public interface ISessionMetaStore {

    Optional<SessionMeta> find(String sessionId);

    /**
     * 全部元数据（P10-6：删除工作区时按 workspaceCode 反查解绑）。
     * <p>无元数据文件的会话不会出现在这里——绑定信息本就只存在元数据里，故不影响解绑完整性。
     */
    List<SessionMeta> findAll();

    void save(SessionMeta meta);
}

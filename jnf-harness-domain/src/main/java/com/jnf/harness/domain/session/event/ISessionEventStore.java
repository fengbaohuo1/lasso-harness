package com.jnf.harness.domain.session.event;

import java.util.List;

/**
 * 事件存储端口：会话事件的持久化（DB / JSONL 等实现见 infrastructure）。
 */
public interface ISessionEventStore {

    /** 追加一条事件（幂等要求：同 session + seq 不重复写入）。 */
    void append(String sessionId, SessionEventEntry entry);

    /** 加载会话全部事件（按 seq 升序）。 */
    List<SessionEventEntry> load(String sessionId);
}

package com.jnf.harness.domain.session.event;

import java.util.List;

/**
 * 写透会话日志：内存 WAL + 持久化写穿（先落内存再写存储，失败上抛）。
 * <p>后续如需异步落库，仅替换本类的写策略，不动 Agent 循环。
 */
public class PersistingSessionLog {

    private final String sessionId;
    private final SessionLog delegate;
    private final ISessionEventStore store;

    public PersistingSessionLog(String sessionId, SessionLog delegate, ISessionEventStore store) {
        this.sessionId = sessionId;
        this.delegate = delegate;
        this.store = store;
    }

    public SessionEventEntry append(SessionEvent event) {
        SessionEventEntry entry = delegate.append(event);
        if (store != null) {
            store.append(sessionId, entry);
        }
        return entry;
    }

    public List<SessionEventEntry> appendBatch(List<SessionEvent> events) {
        List<SessionEventEntry> result = delegate.appendBatch(events);
        if (store != null) {
            for (SessionEventEntry entry : result) {
                store.append(sessionId, entry);
            }
        }
        return result;
    }

    public SessionLog log() {
        return delegate;
    }

    public String sessionId() {
        return sessionId;
    }
}

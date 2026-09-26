package com.jnf.harness.domain.agent.service.run;

import com.jnf.harness.domain.agent.model.AgentOptions;
import com.jnf.harness.domain.agent.service.SystemPromptAssembler;
import com.jnf.harness.domain.agent.service.ToolRunner;
import com.jnf.harness.domain.agent.service.compaction.CompactionEngine;
import com.jnf.harness.domain.llm.adapter.port.ILlmRuntimePort;
import com.jnf.harness.domain.session.event.ISessionEventStore;
import com.jnf.harness.domain.session.event.PersistingSessionLog;
import com.jnf.harness.domain.session.event.SessionEventEntry;
import com.jnf.harness.domain.session.event.SessionLog;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * AgentRun 工厂（组合根）：按 agentId 装配/复用 {@link ReactLoopAgent}。
 * <p>会话以 agentId 为标识；已有持久化事件时重建会话（断点续跑）。
 */
public class AgentRunFactory {

    private final ILlmRuntimePort llm;
    private final ToolRunner toolRunner;
    private final Supplier<AgentOptions> optionsSupplier;
    private final Supplier<List<Map<String, Object>>> toolSchemas;
    private final ISessionEventStore store;
    private final SystemPromptAssembler promptAssembler;
    private final CompactionEngine compactionEngine;
    private final Map<String, ReactLoopAgent> agents = new ConcurrentHashMap<>();

    public AgentRunFactory(ILlmRuntimePort llm, ToolRunner toolRunner,
                           Supplier<AgentOptions> optionsSupplier,
                           Supplier<List<Map<String, Object>>> toolSchemas,
                           ISessionEventStore store,
                           SystemPromptAssembler promptAssembler,
                           CompactionEngine compactionEngine) {
        this.llm = llm;
        this.toolRunner = toolRunner;
        this.optionsSupplier = optionsSupplier;
        this.toolSchemas = toolSchemas;
        this.store = store;
        this.promptAssembler = promptAssembler;
        this.compactionEngine = compactionEngine;
    }

    public ReactLoopAgent getOrCreate(String agentId, AgentEventListener defaultListener) {
        return agents.computeIfAbsent(agentId, id -> {
            SessionLog log = new SessionLog();
            if (store != null) {
                List<SessionEventEntry> restored = store.load(id);
                if (!restored.isEmpty()) {
                    log.appendBatch(restored.stream().map(SessionEventEntry::event).toList());
                }
            }
            PersistingSessionLog session = new PersistingSessionLog(id, log, store);
            return new ReactLoopAgent(id, session, llm, optionsSupplier.get(),
                    toolRunner, toolSchemas, promptAssembler, compactionEngine, defaultListener);
        });
    }

    public Optional<ReactLoopAgent> get(String agentId) {
        return Optional.ofNullable(agents.get(agentId));
    }

    public void shutdownAll() {
        agents.values().forEach(ReactLoopAgent::shutdown);
        agents.clear();
    }
}

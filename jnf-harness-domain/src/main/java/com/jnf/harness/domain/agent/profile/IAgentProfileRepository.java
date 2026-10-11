package com.jnf.harness.domain.agent.profile;

import java.util.List;
import java.util.Optional;

/**
 * 画像仓储端口。
 */
public interface IAgentProfileRepository {

    Optional<AgentProfile> find(String profileCode);

    List<AgentProfile> findAll();

    void upsert(AgentProfile profile);

    void delete(String profileCode);
}

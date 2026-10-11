package com.jnf.harness.domain.agent.profile;

import java.util.List;
import java.util.Optional;

/**
 * 画像服务（A1）：解析（缺失回落缺省）、upsert、删除保护（default 不可删）。
 */
public class AgentProfileService {

    private final IAgentProfileRepository repository;

    public AgentProfileService(IAgentProfileRepository repository) {
        this.repository = repository;
    }

    /** 解析画像：空码/未知码回落缺省画像。 */
    public AgentProfile resolve(String profileCode) {
        if (profileCode == null || profileCode.isBlank()) {
            return AgentProfile.defaults();
        }
        return repository.find(profileCode.trim()).orElseGet(AgentProfile::defaults);
    }

    public Optional<AgentProfile> find(String profileCode) {
        return repository.find(profileCode);
    }

    public List<AgentProfile> list() {
        return repository.findAll();
    }

    public AgentProfile upsert(AgentProfile profile) {
        repository.upsert(profile);
        return profile;
    }

    public void delete(String profileCode) {
        if (AgentProfile.DEFAULT_CODE.equals(profileCode == null ? null : profileCode.trim())) {
            throw new IllegalStateException("default 画像不可删除");
        }
        repository.delete(profileCode.trim());
    }
}

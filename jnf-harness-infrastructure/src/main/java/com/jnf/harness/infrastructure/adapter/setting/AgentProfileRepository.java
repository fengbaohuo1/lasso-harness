package com.jnf.harness.infrastructure.adapter.setting;

import com.jnf.harness.domain.agent.profile.AgentProfile;
import com.jnf.harness.domain.agent.profile.IAgentProfileRepository;
import com.jnf.harness.infrastructure.dao.AgentProfileDao;
import com.jnf.harness.infrastructure.dao.po.AgentProfilePO;

import java.util.List;
import java.util.Optional;

/**
 * 画像仓储（MyBatis 实现，A1）：upsert = update 未命中则 insert（H2/MySQL 双兼容）。
 */
public class AgentProfileRepository implements IAgentProfileRepository {

    private final AgentProfileDao dao;

    public AgentProfileRepository(AgentProfileDao dao) {
        this.dao = dao;
    }

    @Override
    public Optional<AgentProfile> find(String profileCode) {
        return Optional.ofNullable(dao.findByCode(profileCode)).map(AgentProfileRepository::toRecord);
    }

    @Override
    public List<AgentProfile> findAll() {
        return dao.findAll().stream().map(AgentProfileRepository::toRecord).toList();
    }

    @Override
    public void upsert(AgentProfile profile) {
        AgentProfilePO po = toPo(profile);
        if (dao.update(po) == 0) {
            dao.insert(po);
        }
    }

    @Override
    public void delete(String profileCode) {
        dao.delete(profileCode);
    }

    private static AgentProfile toRecord(AgentProfilePO po) {
        return new AgentProfile(po.getProfileCode(), po.getDisplayName(),
                po.getMaxStepsPerTurn(), po.getMaxTokenContinuations(),
                po.getContextTokenBudget(), po.getKeepRecentMessages(),
                po.getCompactionThresholdTokens(), po.getMaxTokens(), po.getTemperature(),
                po.getToolProfile(), po.getPromptExtra());
    }

    private static AgentProfilePO toPo(AgentProfile profile) {
        AgentProfilePO po = new AgentProfilePO();
        po.setProfileCode(profile.profileCode());
        po.setDisplayName(profile.displayName() == null ? profile.profileCode() : profile.displayName());
        po.setMaxStepsPerTurn(profile.maxStepsPerTurn());
        po.setMaxTokenContinuations(profile.maxTokenContinuations());
        po.setContextTokenBudget(profile.contextTokenBudget());
        po.setKeepRecentMessages(profile.keepRecentMessages());
        po.setCompactionThresholdTokens(profile.compactionThresholdTokens());
        po.setMaxTokens(profile.maxTokens());
        po.setTemperature(profile.temperature());
        po.setToolProfile(profile.toolProfile());
        po.setPromptExtra(profile.promptExtra());
        return po;
    }
}

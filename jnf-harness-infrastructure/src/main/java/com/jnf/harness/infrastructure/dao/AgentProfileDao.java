package com.jnf.harness.infrastructure.dao;

import com.jnf.harness.infrastructure.dao.po.AgentProfilePO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 智能体画像 DAO（harness_agent_profile，A1）。
 */
@Mapper
public interface AgentProfileDao {

    @Select("SELECT profile_code, display_name, max_steps_per_turn, max_token_continuations, "
            + "context_token_budget, keep_recent_messages, compaction_threshold_tokens, "
            + "max_tokens, temperature, tool_profile, prompt_extra "
            + "FROM harness_agent_profile WHERE profile_code = #{profileCode}")
    AgentProfilePO findByCode(@Param("profileCode") String profileCode);

    @Select("SELECT profile_code, display_name, max_steps_per_turn, max_token_continuations, "
            + "context_token_budget, keep_recent_messages, compaction_threshold_tokens, "
            + "max_tokens, temperature, tool_profile, prompt_extra "
            + "FROM harness_agent_profile ORDER BY profile_code")
    List<AgentProfilePO> findAll();

    @Insert("INSERT INTO harness_agent_profile (profile_code, display_name, max_steps_per_turn, "
            + "max_token_continuations, context_token_budget, keep_recent_messages, "
            + "compaction_threshold_tokens, max_tokens, temperature, tool_profile, prompt_extra) "
            + "VALUES (#{profileCode}, #{displayName}, #{maxStepsPerTurn}, #{maxTokenContinuations}, "
            + "#{contextTokenBudget}, #{keepRecentMessages}, #{compactionThresholdTokens}, "
            + "#{maxTokens}, #{temperature}, #{toolProfile}, #{promptExtra})")
    int insert(AgentProfilePO po);

    @Update("UPDATE harness_agent_profile SET display_name = #{displayName}, "
            + "max_steps_per_turn = #{maxStepsPerTurn}, "
            + "max_token_continuations = #{maxTokenContinuations}, "
            + "context_token_budget = #{contextTokenBudget}, "
            + "keep_recent_messages = #{keepRecentMessages}, "
            + "compaction_threshold_tokens = #{compactionThresholdTokens}, "
            + "max_tokens = #{maxTokens}, temperature = #{temperature}, "
            + "tool_profile = #{toolProfile}, prompt_extra = #{promptExtra} "
            + "WHERE profile_code = #{profileCode}")
    int update(AgentProfilePO po);

    @Delete("DELETE FROM harness_agent_profile WHERE profile_code = #{profileCode}")
    int delete(@Param("profileCode") String profileCode);
}

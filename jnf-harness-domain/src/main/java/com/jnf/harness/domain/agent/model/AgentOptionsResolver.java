package com.jnf.harness.domain.agent.model;

/**
 * Agent 选项解析器（F2/A1/P9-8）：按 per-turn 提示解析运行选项。
 *
 * @param modelHint    模型提示（可空：命中 modelCode 整渠道切换，未命中作模型名覆盖）
 * @param profileHint  智能体预设码（可空：回落 default 预设）
 * @param thinkingHint 思考强度提示（可空：回落预设的 reasoningEffort）
 */
@FunctionalInterface
public interface AgentOptionsResolver {

    AgentOptions resolve(String modelHint, String profileHint, String thinkingHint);
}

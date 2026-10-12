package com.jnf.harness.domain.tool.service;

import java.util.Map;

/**
 * 审批闸缝：高风险工具调用前的放行判断（修正项 R1 的运行期审批接线点）。
 */
@FunctionalInterface
public interface ApprovalGate {

    /** 默认全放行；第 10 节按画像接通运行期审批。 */
    ApprovalGate ALLOW_ALL = (toolName, arguments) -> true;

    boolean allow(String toolName, Map<String, Object> arguments);

    /** 会话感知形态（P9）：审批记录落真实 sessionId，默认回落无会话形态。 */
    default boolean allow(com.jnf.harness.model.ToolRunContext context, String toolName,
                          Map<String, Object> arguments) {
        return allow(toolName, arguments);
    }
}

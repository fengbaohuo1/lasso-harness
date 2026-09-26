package com.jnf.harness.domain.tool.service;

import com.jnf.harness.model.ToolRunContext;

/**
 * 默认工具执行上下文（agentId/sessionId/cwd 三元组）。
 */
public record DefaultToolRunContext(String agentId, String sessionId, String cwd) implements ToolRunContext {
}

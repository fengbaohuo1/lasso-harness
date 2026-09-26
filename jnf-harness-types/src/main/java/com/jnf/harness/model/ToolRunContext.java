package com.jnf.harness.model;

/**
 * 工具执行上下文：工具运行期可见的最小宿主信息。
 */
public interface ToolRunContext {

    /** 当前 Agent 标识。 */
    String agentId();

    /** 当前会话标识。 */
    String sessionId();

    /** 当前工作目录（工具解析相对路径的基准）。 */
    String cwd();
}

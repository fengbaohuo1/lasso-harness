package com.jnf.harness.spi.host;

/**
 * 子智能体宿主服务（P8-3）：插件经 {@code PluginContext.lookupService(AgentRunService.class)}
 * 把独立子任务派发给子智能体（独立会话/独立预算），宿主治理深度与超时。
 *
 * <p>递归防护是宿主职责：子会话再派发孙会话由深度表裁决（默认 ≤2 层）。
 */
public interface AgentRunService {

    /** 运行一个子智能体回合（阻塞至完成/超时）。 */
    SubagentOutcome run(SubagentRequest request);

    /**
     * 子任务请求。
     *
     * @param parentSessionId 父会话 id（深度治理用；顶层任务传父会话）
     * @param task            子任务描述（必填）
     * @param context         补充上下文（可空，拼在任务前）
     * @param profile         画像码（可空 = default 画像）
     * @param timeoutSeconds  超时秒数（≤0 用宿主默认）
     */
    record SubagentRequest(String parentSessionId, String task, String context,
                           String profile, int timeoutSeconds) {
    }

    /**
     * 子任务结果。
     *
     * @param ok      是否成功（超时/过深/异常为 false）
     * @param sessionId 子会话 id（事件可回放审计；失败可空）
     * @param text    子智能体最终汇报
     * @param turn    子会话完成的轮次
     * @param error   失败原因（成功为 null）
     */
    record SubagentOutcome(boolean ok, String sessionId, String text, long turn, String error) {
    }
}

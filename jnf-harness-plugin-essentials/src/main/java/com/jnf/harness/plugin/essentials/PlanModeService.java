package com.jnf.harness.plugin.essentials;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 计划模式服务（plan 域）：进入/退出计划模式（transient 状态，按会话隔离）。
 */
public class PlanModeService {

    public record PlanState(String sessionId, boolean planMode, String plan) {
    }

    private final Map<String, PlanState> states = new ConcurrentHashMap<>();

    public PlanState enter(String sessionId, String plan) {
        PlanState state = new PlanState(sessionId, true, plan == null ? "" : plan);
        states.put(sessionId, state);
        return state;
    }

    public PlanState exit(String sessionId) {
        PlanState state = new PlanState(sessionId, false, "");
        states.put(sessionId, state);
        return state;
    }

    public PlanState get(String sessionId) {
        return states.getOrDefault(sessionId, new PlanState(sessionId, false, ""));
    }
}

package com.jnf.harness.domain.goal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 目标服务（goal 域）：会话级续期目标快照（ACTIVE/PAUSED/BLOCKED/COMPLETED/CLEARED）。
 */
public class GoalService {

    /** 目标快照。 */
    public record GoalSnapshot(String sessionId, String goalId, long revision, String title,
                               String description, String status, String blockedReason,
                               int maxRounds, int roundsStarted) {
    }

    /** 目标仓储端口（infra 持久化到 harness_goal_state）。 */
    public interface IGoalRepository {
        void save(GoalSnapshot snapshot);

        GoalSnapshot findBySession(String sessionId);
    }

    private final IGoalRepository repository;
    private final Map<String, GoalSnapshot> cache = new ConcurrentHashMap<>();

    public GoalService(IGoalRepository repository) {
        this.repository = repository;
    }

    /** 更新目标（整快照覆盖，revision 自增）。 */
    public GoalSnapshot update(String sessionId, String title, String description,
                               String status, int maxRounds) {
        GoalSnapshot previous = get(sessionId);
        long revision = previous == null ? 1 : previous.revision() + 1;
        int roundsStarted = previous == null ? 0 : previous.roundsStarted();
        GoalSnapshot snapshot = new GoalSnapshot(sessionId, sessionId, revision, title,
                description, status == null ? "ACTIVE" : status, "", maxRounds, roundsStarted);
        cache.put(sessionId, snapshot);
        repository.save(snapshot);
        return snapshot;
    }

    /** 记一轮推进。 */
    public GoalSnapshot tick(String sessionId) {
        GoalSnapshot current = get(sessionId);
        if (current == null) {
            return null;
        }
        GoalSnapshot next = new GoalSnapshot(current.sessionId(), current.goalId(),
                current.revision() + 1, current.title(), current.description(), current.status(),
                current.blockedReason(), current.maxRounds(), current.roundsStarted() + 1);
        cache.put(sessionId, next);
        repository.save(next);
        return next;
    }

    public GoalSnapshot get(String sessionId) {
        GoalSnapshot cached = cache.get(sessionId);
        if (cached != null) {
            return cached;
        }
        GoalSnapshot loaded = repository.findBySession(sessionId);
        if (loaded != null) {
            cache.put(sessionId, loaded);
        }
        return loaded;
    }

    public void clear(String sessionId) {
        cache.remove(sessionId);
        repository.save(new GoalSnapshot(sessionId, sessionId, 0, "", "",
                "CLEARED", "", 0, 0));
    }
}

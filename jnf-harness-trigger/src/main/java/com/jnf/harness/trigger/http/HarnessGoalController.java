package com.jnf.harness.trigger.http;

import com.jnf.harness.api.response.Response;
import com.jnf.harness.domain.goal.GoalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 目标查询/更新（goal 域 REST）。
 */
@RestController
@RequestMapping("/api/harness/goals")
public class HarnessGoalController {

    private final GoalService goalService;

    public HarnessGoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @GetMapping("/{sessionId}")
    public Response<GoalService.GoalSnapshot> get(@PathVariable String sessionId) {
        return Response.success(goalService.get(sessionId));
    }

    @PostMapping("/{sessionId}")
    public Response<GoalService.GoalSnapshot> update(@PathVariable String sessionId,
                                                     @RequestBody Map<String, Object> body) {
        int maxRounds;
        try {
            maxRounds = Integer.parseInt(String.valueOf(body.getOrDefault("maxRounds", "0")));
        } catch (NumberFormatException e) {
            maxRounds = 0;
        }
        return Response.success(goalService.update(sessionId,
                String.valueOf(body.getOrDefault("title", "")),
                String.valueOf(body.getOrDefault("description", "")),
                String.valueOf(body.getOrDefault("status", "ACTIVE")), maxRounds));
    }
}

package com.jnf.harness.trigger.http;

import com.jnf.harness.api.response.Response;
import com.jnf.harness.domain.agent.profile.AgentProfile;
import com.jnf.harness.domain.agent.profile.AgentProfileService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 智能体画像 REST（A1）：步数/预算/工具画像/提示词补丁按场景裁运行开销。
 * <p>PUT 为「已有值合并」语义（缺省字段沿用原画像/缺省画像），default 画像不可删。
 */
@RestController
@RequestMapping("/api/harness/profiles")
public class HarnessProfileController {

    private final AgentProfileService service;

    public HarnessProfileController(AgentProfileService service) {
        this.service = service;
    }

    @GetMapping
    public Response<List<AgentProfile>> list() {
        return Response.success(service.list());
    }

    @GetMapping("/{profileCode}")
    public Response<AgentProfile> get(@PathVariable String profileCode) {
        return service.find(profileCode)
                .map(Response::success)
                .orElseGet(() -> Response.invalidArgument("画像不存在: " + profileCode));
    }

    @PutMapping("/{profileCode}")
    public Response<AgentProfile> save(@PathVariable String profileCode,
                                       @RequestBody Map<String, Object> body) {
        try {
            AgentProfile base = service.find(profileCode).orElseGet(AgentProfile::defaults);
            AgentProfile updated = new AgentProfile(
                    profileCode,
                    str(body, "displayName", base.displayName()),
                    integer(body, "maxStepsPerTurn", base.maxStepsPerTurn()),
                    integer(body, "maxTokenContinuations", base.maxTokenContinuations()),
                    longValue(body, "contextTokenBudget", base.contextTokenBudget()),
                    integer(body, "keepRecentMessages", base.keepRecentMessages()),
                    longValue(body, "compactionThresholdTokens", base.compactionThresholdTokens()),
                    optionalInteger(body, "maxTokens", base.maxTokens()),
                    optionalDouble(body, "temperature", base.temperature()),
                    str(body, "toolProfile", base.toolProfile()),
                    str(body, "promptExtra", base.promptExtra()));
            return Response.success(service.upsert(updated));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Response.invalidArgument(e.getMessage());
        }
    }

    @DeleteMapping("/{profileCode}")
    public Response<Void> delete(@PathVariable String profileCode) {
        try {
            service.delete(profileCode);
            return Response.success(null);
        } catch (IllegalStateException e) {
            return Response.invalidArgument(e.getMessage());
        }
    }

    private static String str(Map<String, Object> body, String key, String fallback) {
        Object value = body.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static int integer(Map<String, Object> body, String key, int fallback) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return Integer.parseInt(String.valueOf(value).trim());
    }

    private static long longValue(Map<String, Object> body, String key, long fallback) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return Long.parseLong(String.valueOf(value).trim());
    }

    private static Integer optionalInteger(Map<String, Object> body, String key, Integer fallback) {
        Object value = body.get(key);
        return value == null || String.valueOf(value).isBlank()
                ? fallback : Integer.valueOf(String.valueOf(value).trim());
    }

    private static Double optionalDouble(Map<String, Object> body, String key, Double fallback) {
        Object value = body.get(key);
        return value == null || String.valueOf(value).isBlank()
                ? fallback : Double.valueOf(String.valueOf(value).trim());
    }
}

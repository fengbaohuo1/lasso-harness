package com.jnf.harness.trigger.http;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent Card 发现面（/.well-known）：DSH v1 私有卡 + A2A 0.3.0 标准卡 + 旧版兼容路径。
 */
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "harness.capabilities.a2a", havingValue = "true", matchIfMissing = true)
public class AgentCardController {

    private final String displayName;

    public AgentCardController(@Value("${harness.agent-card.display-name:jnf-harness}") String displayName) {
        this.displayName = displayName;
    }

    /** DSH 私有卡（dsh.v1）。 */
    @GetMapping("/.well-known/dsh-agent-card")
    public Map<String, Object> dshCard() {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("protocol", "dsh.v1");
        card.put("schemaVersion", "1.0");
        card.put("displayName", displayName);
        card.put("purpose", "通用 Agent Harness（ReAct 循环 + 工具 + 插件）");
        card.put("capabilities", List.of("chat", "tools", "plugins", "tasks", "approvals"));
        card.put("endpoints", Map.of(
                "stream", "/api/agent/stream",
                "approvals", "/api/harness/approvals"));
        return card;
    }

    /** A2A 标准卡（0.3.0）。 */
    @GetMapping("/.well-known/agent-card.json")
    public Map<String, Object> a2aCard() {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("protocolVersion", "0.3.0");
        card.put("name", displayName);
        card.put("description", "jnf-harness A2A agent");
        card.put("url", "/a2a");
        card.put("preferredTransport", "JSONRPC");
        card.put("capabilities", Map.of("streaming", true,
                "pushNotifications", false, "stateTransitionHistory", false));
        card.put("defaultInputModes", List.of("text/plain"));
        card.put("defaultOutputModes", List.of("text/plain"));
        card.put("skills", List.of(Map.of("id", "agent-task", "name", "agent-task")));
        card.put("version", "0.1.0");
        return card;
    }

    /** 旧版 A2A（≤0.2.x）兼容路径：同一张卡。 */
    @GetMapping("/.well-known/agent.json")
    public Map<String, Object> legacyA2aCard() {
        return a2aCard();
    }
}

package com.jnf.harness.trigger.http;

import com.jnf.harness.api.response.Response;
import com.jnf.harness.domain.digitalhuman.DigitalHumanService;
import com.jnf.harness.infrastructure.adapter.web.RemoteAgentDiscoveryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 数字人目录（身份 + 端点 + Agent Card 探测）。
 */
@RestController
@RequestMapping("/api/digital-humans")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "harness.capabilities.digital-human", havingValue = "true", matchIfMissing = true)
public class DigitalHumanController {

    private final DigitalHumanService service;
    private final RemoteAgentDiscoveryService discovery;

    public DigitalHumanController(DigitalHumanService service, RemoteAgentDiscoveryService discovery) {
        this.service = service;
        this.discovery = discovery;
    }

    @GetMapping
    public Response<List<DigitalHumanService.DigitalHuman>> list() {
        return Response.success(service.list());
    }

    @PostMapping
    public Response<DigitalHumanService.DigitalHuman> create(@RequestBody Map<String, Object> body) {
        return Response.success(service.create(
                String.valueOf(body.getOrDefault("displayName", "")),
                String.valueOf(body.getOrDefault("purpose", "")),
                String.valueOf(body.getOrDefault("roleTags", ""))));
    }

    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Response.success(null);
    }

    /** 挂载端点（可先 discover 探测卡片）。 */
    @PostMapping("/{id}/endpoints")
    public Response<DigitalHumanService.Endpoint> attach(@PathVariable String id,
                                                         @RequestBody Map<String, Object> body) {
        String baseUrl = String.valueOf(body.getOrDefault("baseUrl", ""));
        String card = String.valueOf(body.getOrDefault("agentCard", ""));
        if (card.isBlank()) {
            card = discovery.discover(baseUrl);
        }
        return Response.success(service.attachEndpoint(id,
                String.valueOf(body.getOrDefault("endpointType", "remote-dsh")), baseUrl,
                String.valueOf(body.getOrDefault("credentialRef", "")),
                String.valueOf(body.getOrDefault("protocolVersion", "dsh.v1")), card));
    }

    /** 探测远端 Agent Card。 */
    @PostMapping("/discover")
    public Response<String> discover(@RequestBody Map<String, Object> body) {
        return Response.success(discovery.discover(String.valueOf(body.getOrDefault("baseUrl", ""))));
    }
}

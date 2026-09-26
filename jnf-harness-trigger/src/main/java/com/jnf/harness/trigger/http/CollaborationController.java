package com.jnf.harness.trigger.http;

import com.jnf.harness.api.response.Response;
import com.jnf.harness.domain.collaboration.CollaborationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 协作房间 REST（建房/入群/发消息/事件续传）。
 */
@RestController
@RequestMapping("/api/collaboration")
public class CollaborationController {

    private final CollaborationService service;

    public CollaborationController(CollaborationService service) {
        this.service = service;
    }

    @PostMapping("/rooms")
    public Response<CollaborationService.Room> createRoom(@RequestBody Map<String, Object> body) {
        return Response.success(service.createRoom(
                String.valueOf(body.getOrDefault("workspaceId", "default")),
                String.valueOf(body.getOrDefault("title", "")),
                String.valueOf(body.getOrDefault("objective", ""))));
    }

    @GetMapping("/rooms/{roomId}")
    public Response<Map<String, Object>> room(@PathVariable String roomId) {
        return Response.success(Map.of(
                "room", service.getRoom(roomId),
                "participants", service.participants(roomId)));
    }

    @PostMapping("/rooms/{roomId}/participants")
    public Response<CollaborationService.Participant> join(@PathVariable String roomId,
                                                           @RequestBody Map<String, Object> body) {
        return Response.success(service.join(roomId,
                String.valueOf(body.getOrDefault("digitalHumanId", "")),
                String.valueOf(body.getOrDefault("role", "member"))));
    }

    @PostMapping("/rooms/{roomId}/messages")
    public Response<CollaborationService.RoomEvent> message(@PathVariable String roomId,
                                                            @RequestBody Map<String, Object> body) {
        return Response.success(service.postMessage(roomId,
                String.valueOf(body.getOrDefault("from", "user")),
                String.valueOf(body.getOrDefault("text", ""))));
    }

    /** 事件续传：afterSeq 之后的事件（seq 升序）。 */
    @GetMapping("/rooms/{roomId}/events")
    public Response<List<CollaborationService.RoomEvent>> events(@PathVariable String roomId,
                                                                 @RequestParam(defaultValue = "0") long afterSeq) {
        return Response.success(service.eventsAfter(roomId, afterSeq));
    }
}

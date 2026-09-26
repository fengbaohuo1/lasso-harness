package com.jnf.harness.domain.collaboration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 协作房间服务（第 13b 节）：room/participant/room_event（事件溯源 + seq 续传）。
 * <p>room_event 是"一切展示与恢复的源头"：seq 按房间单调递增（(room_id, seq) 唯一）。
 */
public class CollaborationService {

    /** 房间。 */
    public record Room(String id, String workspaceId, String title, String objective,
                       String orchestrationMode, String runState) {
    }

    /** 参与者。 */
    public record Participant(String id, String roomId, String digitalHumanId,
                              String roomRole, String presence) {
    }

    /** 房间事件。 */
    public record RoomEvent(String id, String roomId, long seq, String eventType,
                            String payload, Instant occurredAt) {
    }

    /** 持久化端口。 */
    public interface IRepository {
        void saveRoom(Room room);

        Room findRoom(String roomId);

        void saveParticipant(Participant participant);

        List<Participant> findParticipants(String roomId);

        void saveEvent(RoomEvent event);

        List<RoomEvent> findEvents(String roomId, long afterSeq);

        long maxSeq(String roomId);
    }

    private final IRepository repository;
    private final Map<String, Object> seqLocks = new ConcurrentHashMap<>();

    public CollaborationService(IRepository repository) {
        this.repository = repository;
    }

    public Room createRoom(String workspaceId, String title, String objective) {
        Room room = new Room(UUID.randomUUID().toString(), workspaceId, title, objective, "MANUAL", "idle");
        repository.saveRoom(room);
        appendEvent(room.id(), "ROOM_CREATED", "{\"title\":\"" + title + "\"}");
        return room;
    }

    public Room getRoom(String roomId) {
        Room room = repository.findRoom(roomId);
        if (room == null) {
            throw new IllegalArgumentException("房间不存在: " + roomId);
        }
        return room;
    }

    public Participant join(String roomId, String digitalHumanId, String role) {
        getRoom(roomId);
        Participant participant = new Participant(UUID.randomUUID().toString(), roomId,
                digitalHumanId, role == null ? "member" : role, "idle");
        repository.saveParticipant(participant);
        appendEvent(roomId, "PARTICIPANT_JOINED",
                "{\"digitalHumanId\":\"" + digitalHumanId + "\"}");
        return participant;
    }

    /** 发消息（事件即消息）。 */
    public RoomEvent postMessage(String roomId, String from, String text) {
        getRoom(roomId);
        return appendEvent(roomId, "MESSAGE_CREATED",
                "{\"from\":\"" + from + "\",\"text\":\"" + escape(text) + "\"}");
    }

    /** 续传语义：取 afterSeq 之后的事件（seq 升序）。 */
    public List<RoomEvent> eventsAfter(String roomId, long afterSeq) {
        return new ArrayList<>(repository.findEvents(roomId, afterSeq));
    }

    public List<Participant> participants(String roomId) {
        return new ArrayList<>(repository.findParticipants(roomId));
    }

    private RoomEvent appendEvent(String roomId, String type, String payload) {
        synchronized (seqLocks.computeIfAbsent(roomId, k -> new Object())) {
            long seq = repository.maxSeq(roomId) + 1;
            RoomEvent event = new RoomEvent(UUID.randomUUID().toString(), roomId, seq,
                    type, payload, Instant.now());
            repository.saveEvent(event);
            return event;
        }
    }

    private static String escape(String text) {
        return String.valueOf(text).replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n");
    }
}

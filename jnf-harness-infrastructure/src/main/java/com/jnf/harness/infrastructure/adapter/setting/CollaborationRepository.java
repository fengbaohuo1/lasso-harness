package com.jnf.harness.infrastructure.adapter.setting;

import com.jnf.harness.domain.collaboration.CollaborationService;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * 协作房间持久化（JdbcTemplate：collaboration_room / room_participant / room_event）。
 */
public class CollaborationRepository implements CollaborationService.IRepository {

    private final JdbcTemplate jdbc;

    public CollaborationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void saveRoom(CollaborationService.Room room) {
        jdbc.update("INSERT INTO collaboration_room (id, workspace_id, title, objective, orchestration_mode, "
                        + "run_state, created_at) VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP) "
                        + "ON DUPLICATE KEY UPDATE title = VALUES(title), objective = VALUES(objective), "
                        + "run_state = VALUES(run_state)",
                room.id(), room.workspaceId(), room.title(), room.objective(),
                room.orchestrationMode(), room.runState());
    }

    @Override
    public CollaborationService.Room findRoom(String roomId) {
        List<CollaborationService.Room> rows = jdbc.query(
                "SELECT * FROM collaboration_room WHERE id = ?", (rs, i) ->
                        new CollaborationService.Room(rs.getString("id"), rs.getString("workspace_id"),
                                rs.getString("title"), rs.getString("objective"),
                                rs.getString("orchestration_mode"), rs.getString("run_state")), roomId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public void saveParticipant(CollaborationService.Participant participant) {
        jdbc.update("INSERT INTO room_participant (id, room_id, digital_human_id, room_role, presence, joined_at) "
                        + "VALUES (?,?,?,?,?,CURRENT_TIMESTAMP) "
                        + "ON DUPLICATE KEY UPDATE room_role = VALUES(room_role), presence = VALUES(presence)",
                participant.id(), participant.roomId(), participant.digitalHumanId(),
                participant.roomRole(), participant.presence());
    }

    @Override
    public List<CollaborationService.Participant> findParticipants(String roomId) {
        return jdbc.query("SELECT * FROM room_participant WHERE room_id = ? ORDER BY joined_at",
                (rs, i) -> new CollaborationService.Participant(rs.getString("id"), rs.getString("room_id"),
                        rs.getString("digital_human_id"), rs.getString("room_role"),
                        rs.getString("presence")), roomId);
    }

    @Override
    public void saveEvent(CollaborationService.RoomEvent event) {
        jdbc.update("INSERT INTO room_event (id, room_id, seq, event_type, payload, occurred_at) "
                        + "VALUES (?,?,?,?,?,?)",
                event.id(), event.roomId(), event.seq(), event.eventType(), event.payload(),
                toTimestamp(event.occurredAt()));
    }

    @Override
    public List<CollaborationService.RoomEvent> findEvents(String roomId, long afterSeq) {
        return jdbc.query("SELECT * FROM room_event WHERE room_id = ? AND seq > ? ORDER BY seq",
                (rs, i) -> mapEvent(rs), roomId, afterSeq);
    }

    @Override
    public long maxSeq(String roomId) {
        Long max = jdbc.query("SELECT COALESCE(MAX(seq), 0) FROM room_event WHERE room_id = ?",
                rs -> rs.next() ? rs.getLong(1) : 0L, roomId);
        return max == null ? 0L : max;
    }

    private CollaborationService.RoomEvent mapEvent(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp occurred = rs.getTimestamp("occurred_at");
        return new CollaborationService.RoomEvent(rs.getString("id"), rs.getString("room_id"),
                rs.getLong("seq"), rs.getString("event_type"), rs.getString("payload"),
                occurred == null ? null : occurred.toInstant());
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}

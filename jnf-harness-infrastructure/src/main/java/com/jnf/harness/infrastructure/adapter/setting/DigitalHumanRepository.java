package com.jnf.harness.infrastructure.adapter.setting;

import com.jnf.harness.domain.digitalhuman.DigitalHumanService;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * 数字人持久化（JdbcTemplate：digital_human + digital_human_endpoint）。
 */
public class DigitalHumanRepository implements DigitalHumanService.IRepository {

    private final JdbcTemplate jdbc;

    public DigitalHumanRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void saveHuman(DigitalHumanService.DigitalHuman human) {
        jdbc.update("INSERT INTO digital_human (id, display_name, purpose, role_tags, approval_policy, created_at) "
                        + "VALUES (?,?,?,?,?,CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE "
                        + "display_name = VALUES(display_name), purpose = VALUES(purpose), "
                        + "role_tags = VALUES(role_tags), approval_policy = VALUES(approval_policy)",
                human.id(), human.displayName(), human.purpose(), human.roleTags(), human.approvalPolicy());
    }

    @Override
    public void deleteHuman(String id) {
        jdbc.update("DELETE FROM digital_human WHERE id = ?", id);
    }

    @Override
    public List<DigitalHumanService.DigitalHuman> findHumans() {
        return jdbc.query("SELECT * FROM digital_human ORDER BY created_at", (rs, i) ->
                new DigitalHumanService.DigitalHuman(rs.getString("id"), rs.getString("display_name"),
                        rs.getString("purpose"), rs.getString("role_tags"), rs.getString("approval_policy")));
    }

    @Override
    public void saveEndpoint(DigitalHumanService.Endpoint endpoint) {
        jdbc.update("INSERT INTO digital_human_endpoint (id, digital_human_id, endpoint_type, base_url, "
                        + "credential_ref, protocol_version, agent_card, health_state, last_checked_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE "
                        + "endpoint_type = VALUES(endpoint_type), base_url = VALUES(base_url), "
                        + "credential_ref = VALUES(credential_ref), protocol_version = VALUES(protocol_version), "
                        + "agent_card = VALUES(agent_card), health_state = VALUES(health_state)",
                endpoint.id(), endpoint.digitalHumanId(), endpoint.endpointType(), endpoint.baseUrl(),
                endpoint.credentialRef(), endpoint.protocolVersion(), endpoint.agentCard(), endpoint.healthState());
    }

    @Override
    public void deleteEndpoints(String digitalHumanId) {
        jdbc.update("DELETE FROM digital_human_endpoint WHERE digital_human_id = ?", digitalHumanId);
    }

    @Override
    public List<DigitalHumanService.Endpoint> findEndpoints(String digitalHumanId) {
        return jdbc.query("SELECT * FROM digital_human_endpoint WHERE digital_human_id = ?",
                (rs, i) -> new DigitalHumanService.Endpoint(rs.getString("id"),
                        rs.getString("digital_human_id"), rs.getString("endpoint_type"),
                        rs.getString("base_url"), rs.getString("credential_ref"),
                        rs.getString("protocol_version"), rs.getString("agent_card"),
                        rs.getString("health_state")), digitalHumanId);
    }
}

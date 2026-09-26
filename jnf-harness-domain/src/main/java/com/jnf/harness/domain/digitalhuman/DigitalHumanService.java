package com.jnf.harness.domain.digitalhuman;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数字人服务（第 13 节）：身份与连接解耦（同一身份可换端点）。
 */
public class DigitalHumanService {

    /** 数字人身份。 */
    public record DigitalHuman(String id, String displayName, String purpose,
                               String roleTags, String approvalPolicy) {
    }

    /** 连接端点。 */
    public record Endpoint(String id, String digitalHumanId, String endpointType, String baseUrl,
                           String credentialRef, String protocolVersion, String agentCard,
                           String healthState) {
    }

    /** 持久化端口。 */
    public interface IRepository {
        void saveHuman(DigitalHuman human);

        void deleteHuman(String id);

        List<DigitalHuman> findHumans();

        void saveEndpoint(Endpoint endpoint);

        void deleteEndpoints(String digitalHumanId);

        List<Endpoint> findEndpoints(String digitalHumanId);
    }

    private final IRepository repository;

    public DigitalHumanService(IRepository repository) {
        this.repository = repository;
    }

    public DigitalHuman create(String displayName, String purpose, String roleTags) {
        DigitalHuman human = new DigitalHuman(UUID.randomUUID().toString(),
                displayName, purpose, roleTags, "WRITE_REQUIRES_APPROVAL");
        repository.saveHuman(human);
        return human;
    }

    public void delete(String id) {
        repository.deleteEndpoints(id);
        repository.deleteHuman(id);
    }

    public List<DigitalHuman> list() {
        return repository.findHumans();
    }

    public Optional<DigitalHuman> get(String id) {
        return repository.findHumans().stream().filter(h -> h.id().equals(id)).findFirst();
    }

    /** 挂载端点（Agent Card 附带保存）。 */
    public Endpoint attachEndpoint(String digitalHumanId, String endpointType, String baseUrl,
                                   String credentialRef, String protocolVersion, String agentCard) {
        if (get(digitalHumanId).isEmpty()) {
            throw new IllegalArgumentException("数字人不存在: " + digitalHumanId);
        }
        Endpoint endpoint = new Endpoint(UUID.randomUUID().toString(), digitalHumanId,
                endpointType, baseUrl, credentialRef, protocolVersion, agentCard, "unknown");
        repository.saveEndpoint(endpoint);
        return endpoint;
    }

    public List<Endpoint> endpoints(String digitalHumanId) {
        return new ArrayList<>(repository.findEndpoints(digitalHumanId));
    }
}

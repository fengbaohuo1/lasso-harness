package com.jnf.harness.domain.runtime.setting;

import com.jnf.harness.domain.runtime.secret.ISecretStore;
import com.jnf.harness.domain.runtime.secret.SecretRefResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型渠道服务规则（M1/M2）：写入式 apiKey、激活/删除保护。
 */
class ModelSettingServiceTest {

    private final Map<String, ModelSettingEntity> table = new HashMap<>();
    private final Map<String, String> vault = new HashMap<>();
    private String activeCode;

    private final IModelSettingRepository repository = new IModelSettingRepository() {
        @Override
        public void upsert(ModelSettingEntity entity) {
            table.put(entity.channelCode(), entity);
        }

        @Override
        public void delete(String channelCode) {
            table.remove(channelCode);
        }

        @Override
        public List<ModelSettingEntity> findAll() {
            return new ArrayList<>(table.values());
        }

        @Override
        public Optional<ModelSettingEntity> findActive() {
            return Optional.ofNullable(activeCode).map(table::get);
        }

        @Override
        public void activate(String channelCode) {
            activeCode = channelCode;
        }
    };

    private final SecretRefResolver secrets = new SecretRefResolver(new ISecretStore() {
        @Override
        public Optional<String> find(String secretId) {
            return Optional.ofNullable(vault.get(secretId));
        }

        @Override
        public void put(String secretId, String value) {
            vault.put(secretId, value);
        }

        @Override
        public boolean exists(String secretId) {
            return vault.containsKey(secretId);
        }

        @Override
        public void delete(String secretId) {
            vault.remove(secretId);
        }
    }, name -> null);

    private final ModelSettingService service = new ModelSettingService(repository, secrets);

    private static ModelSettingEntity entity(String code, boolean enabled) {
        return new ModelSettingEntity(code, "渠道" + code, "openai", "model-x",
                "http://localhost:1/v1", "", "openai", enabled, false);
    }

    @Test
    @DisplayName("写入式 apiKey 进密钥库，apiKeyRef 为 secret: 引用，可查 hasApiKey/resolve")
    void save_with_api_key() {
        ModelSettingEntity saved = service.save(entity("ch-1", true), "sk-secret");
        assertEquals("secret:apiKey.ch-1", saved.apiKeyRef());
        assertTrue(service.hasApiKey("ch-1"));
        assertEquals("sk-secret", service.resolveApiKey("ch-1"));
    }

    @Test
    @DisplayName("激活：渠道不存在或已禁用 → 拒绝")
    void activate_guards() {
        service.save(entity("ch-off", false));
        assertThrows(IllegalArgumentException.class, () -> service.activate("ch-off"));
        assertThrows(IllegalArgumentException.class, () -> service.activate("nope"));
    }

    @Test
    @DisplayName("删除保护：当前生效渠道不可删")
    void delete_active_guard() {
        service.save(entity("ch-a", true));
        service.save(entity("ch-b", true));
        service.activate("ch-a");
        assertThrows(IllegalArgumentException.class, () -> service.delete("ch-a"));
        service.activate("ch-b");
        service.delete("ch-a");
        assertFalse(service.list().stream().anyMatch(item -> item.channelCode().equals("ch-a")));
    }
}

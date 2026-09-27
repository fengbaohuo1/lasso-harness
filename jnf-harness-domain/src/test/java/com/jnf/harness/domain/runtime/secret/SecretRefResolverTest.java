package com.jnf.harness.domain.runtime.secret;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 密钥引用解析（M1/R9）：env:/secret:/无前缀三种语义。
 */
class SecretRefResolverTest {

    private final Map<String, String> vault = new HashMap<>();
    private final ISecretStore store = new ISecretStore() {
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
    };
    private final Map<String, String> env = Map.of("MY_KEY", "env-value");
    private final SecretRefResolver resolver = new SecretRefResolver(store, env::get);

    @Test
    @DisplayName("env: 前缀与无前缀（兼容）都取环境变量")
    void env_semantics() {
        assertEquals("env-value", resolver.resolve("env:MY_KEY"));
        assertEquals("env-value", resolver.resolve("MY_KEY"));
        assertEquals("", resolver.resolve("env:MISSING"));
    }

    @Test
    @DisplayName("secret: 前缀取密钥库；写入返回引用")
    void secret_semantics() {
        String ref = resolver.putSecret("apiKey.ch1", "sk-123");
        assertEquals("secret:apiKey.ch1", ref);
        assertEquals("sk-123", resolver.resolve(ref));
        assertTrue(resolver.hasSecret(ref));
    }

    @Test
    @DisplayName("空引用/未知引用解析为空串；hasSecret 为 false")
    void blank_and_unknown() {
        assertEquals("", resolver.resolve(null));
        assertEquals("", resolver.resolve("  "));
        assertEquals("", resolver.resolve("secret:nope"));
        assertFalse(resolver.hasSecret("secret:nope"));
    }
}

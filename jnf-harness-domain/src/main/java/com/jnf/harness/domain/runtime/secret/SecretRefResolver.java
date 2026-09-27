package com.jnf.harness.domain.runtime.secret;

import java.util.Objects;
import java.util.function.Function;

/**
 * 密钥引用解析器（R9 配套）：{@code apiKeyRef} 只存引用，取值时解析。
 * <ul>
 *   <li>{@code env:VAR}：环境变量；</li>
 *   <li>{@code secret:ID}：本地密钥库（{@link ISecretStore}）；</li>
 *   <li>无前缀：按环境变量名兼容旧语义。</li>
 * </ul>
 */
public final class SecretRefResolver {

    public static final String ENV_PREFIX = "env:";
    public static final String SECRET_PREFIX = "secret:";

    private final ISecretStore store;
    private final Function<String, String> envLookup;

    public SecretRefResolver(ISecretStore store) {
        this(store, System::getenv);
    }

    /** 可注入环境查找（测试用）。 */
    public SecretRefResolver(ISecretStore store, Function<String, String> envLookup) {
        this.store = Objects.requireNonNull(store, "store");
        this.envLookup = Objects.requireNonNull(envLookup, "envLookup");
    }

    /** 解析引用为密钥值；解析不到返回空串（不抛出，调用方决定严格性）。 */
    public String resolve(String ref) {
        if (ref == null || ref.isBlank()) {
            return "";
        }
        String trimmed = ref.trim();
        if (trimmed.regionMatches(true, 0, SECRET_PREFIX, 0, SECRET_PREFIX.length())) {
            return store.find(trimmed.substring(SECRET_PREFIX.length())).orElse("");
        }
        String envName = trimmed.regionMatches(true, 0, ENV_PREFIX, 0, ENV_PREFIX.length())
                ? trimmed.substring(ENV_PREFIX.length()) : trimmed;
        return envLookup.apply(envName) == null ? "" : envLookup.apply(envName);
    }

    /** 存入密钥库并返回引用（{@code secret:<id>}），供落库。 */
    public String putSecret(String secretId, String value) {
        if (secretId == null || secretId.isBlank()) {
            throw new IllegalArgumentException("secretId 必填");
        }
        store.put(secretId.trim(), value == null ? "" : value);
        return SECRET_PREFIX + secretId.trim();
    }

    /** 引用是否有可用密钥（API 只回 hasApiKey，不回值）。 */
    public boolean hasSecret(String ref) {
        return !resolve(ref).isBlank();
    }
}

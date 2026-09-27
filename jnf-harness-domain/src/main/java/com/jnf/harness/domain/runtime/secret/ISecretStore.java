package com.jnf.harness.domain.runtime.secret;

import java.util.Optional;

/**
 * 密钥存储端口：按 ID 存取密钥值（R9：明文只进密钥库，不入库/入仓/入文档）。
 * <p>引用语义见 {@link SecretRefResolver}：{@code env:VAR} 环境变量、{@code secret:ID} 密钥库。
 */
public interface ISecretStore {

    Optional<String> find(String secretId);

    void put(String secretId, String value);

    boolean exists(String secretId);

    void delete(String secretId);
}

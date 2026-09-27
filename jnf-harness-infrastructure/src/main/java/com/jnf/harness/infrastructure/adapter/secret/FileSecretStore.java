package com.jnf.harness.infrastructure.adapter.secret;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jnf.harness.domain.runtime.secret.ISecretStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 本地文件密钥库：{@code data/secrets.json}，值 Base64 混淆（单机单用户务实解，非 HSM 级——边界见计划 SD1）。
 * <p>R9：文件不入 git（data/ 已忽略），API 层永不回显值。
 */
public class FileSecretStore implements ISecretStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path file;
    private final Map<String, String> values = new HashMap<>();

    public FileSecretStore(Path file) {
        this.file = file;
        load();
    }

    @Override
    public synchronized Optional<String> find(String secretId) {
        return Optional.ofNullable(values.get(secretId)).map(FileSecretStore::decode);
    }

    @Override
    public synchronized void put(String secretId, String value) {
        values.put(secretId, encode(value));
        save();
    }

    @Override
    public synchronized boolean exists(String secretId) {
        return values.containsKey(secretId);
    }

    @Override
    public synchronized void delete(String secretId) {
        if (values.remove(secretId) != null) {
            save();
        }
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            ObjectNode root = (ObjectNode) MAPPER.readTree(Files.readAllBytes(file));
            root.fields().forEachRemaining(entry -> values.put(entry.getKey(), entry.getValue().asText("")));
        } catch (IOException | ClassCastException e) {
            throw new IllegalStateException("密钥库读取失败: " + file + ": " + e.getMessage(), e);
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            ObjectNode root = MAPPER.createObjectNode();
            values.forEach(root::put);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(tmp, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(root));
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("密钥库写入失败: " + file + ": " + e.getMessage(), e);
        }
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String encoded) {
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }
}

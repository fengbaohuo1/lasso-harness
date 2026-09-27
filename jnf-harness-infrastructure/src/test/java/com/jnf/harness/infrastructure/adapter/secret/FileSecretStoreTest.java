package com.jnf.harness.infrastructure.adapter.secret;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地文件密钥库（M1）：读写回环 + 重启（重开实例）不丢。
 */
class FileSecretStoreTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("put/find/delete 回环；值在文件中为 Base64 而非明文")
    void roundtrip_and_obfuscation() throws Exception {
        Path file = tempDir.resolve("secrets.json");
        FileSecretStore store = new FileSecretStore(file);

        store.put("apiKey.ch1", "sk-plain-123");
        assertEquals(Optional.of("sk-plain-123"), store.find("apiKey.ch1"));
        assertTrue(store.exists("apiKey.ch1"));

        String onDisk = Files.readString(file);
        assertFalse(onDisk.contains("sk-plain-123"), "文件不应含明文密钥");

        store.delete("apiKey.ch1");
        assertEquals(Optional.empty(), store.find("apiKey.ch1"));
        assertFalse(store.exists("apiKey.ch1"));
    }

    @Test
    @DisplayName("持久化：重开实例读回既有值")
    void persists_across_instances() {
        Path file = tempDir.resolve("secrets.json");
        new FileSecretStore(file).put("k", "v1");
        FileSecretStore reopened = new FileSecretStore(file);
        assertEquals(Optional.of("v1"), reopened.find("k"));
    }
}

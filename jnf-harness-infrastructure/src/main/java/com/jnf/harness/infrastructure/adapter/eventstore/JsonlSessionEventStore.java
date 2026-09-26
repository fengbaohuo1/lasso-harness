package com.jnf.harness.infrastructure.adapter.eventstore;

import com.jnf.harness.domain.session.event.ISessionEventStore;
import com.jnf.harness.domain.session.event.SessionEventEntry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * JSONL 文件事件存储：每会话一个 {@code <root>/<sessionId>/events.jsonl}，一行一事件。
 * <p>追加持进程内锁；加载按行解码（跳过损坏行并告警，保证可恢复）。
 */
public class JsonlSessionEventStore implements ISessionEventStore {

    private final Path root;
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public JsonlSessionEventStore(Path root) {
        this.root = root;
    }

    @Override
    public void append(String sessionId, SessionEventEntry entry) {
        ReentrantLock lock = locks.computeIfAbsent(sessionId, k -> new ReentrantLock());
        lock.lock();
        try {
            Path file = sessionFile(sessionId);
            Files.createDirectories(file.getParent());
            String line = SessionEventJsonCodec.toJson(entry) + System.lineSeparator();
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("事件落盘失败: " + sessionId, e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<SessionEventEntry> load(String sessionId) {
        Path file = sessionFile(sessionId);
        if (!Files.exists(file)) {
            return List.of();
        }
        List<SessionEventEntry> entries = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    entries.add(SessionEventJsonCodec.fromJson(line));
                } catch (Exception e) {
                    // 跳过损坏行，保证可恢复
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("事件读取失败: " + sessionId, e);
        }
        return entries;
    }

    private Path sessionFile(String sessionId) {
        return root.resolve(sessionId).resolve("events.jsonl");
    }
}

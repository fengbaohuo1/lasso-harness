package com.jnf.harness.infrastructure.adapter.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jnf.harness.domain.session.meta.ISessionMetaStore;
import com.jnf.harness.domain.session.meta.SessionMeta;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * 会话元数据文件存储：{@code <root>/<sessionId>/meta.json}（与 events.jsonl 同目录）。
 * <p>原子写（临时文件 + move），无元数据文件 = 无自定义标题且未删除。
 */
public class FileSessionMetaStore implements ISessionMetaStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path root;

    public FileSessionMetaStore(Path root) {
        this.root = root;
    }

    @Override
    public Optional<SessionMeta> find(String sessionId) {
        Path file = metaFile(sessionId);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            MetaJson json = MAPPER.readValue(file.toFile(), MetaJson.class);
            return Optional.of(new SessionMeta(sessionId,
                    json.title == null || json.title.isBlank() ? null : json.title,
                    json.deleted, json.updatedAt));
        } catch (IOException e) {
            throw new UncheckedIOException("读取会话元数据失败: " + file, e);
        }
    }

    @Override
    public void save(SessionMeta meta) {
        Path file = metaFile(meta.sessionId());
        try {
            Files.createDirectories(file.getParent());
            MetaJson json = new MetaJson();
            json.title = meta.title();
            json.deleted = meta.deleted();
            json.updatedAt = meta.updatedAt();
            Path tmp = file.resolveSibling("meta.json.tmp");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), json);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("写入会话元数据失败: " + file, e);
        }
    }

    private Path metaFile(String sessionId) {
        return root.resolve(sessionId).resolve("meta.json");
    }

    /** JSON 形状（对外 API 的 title/deleted/updatedAt 由此投影）。 */
    public static class MetaJson {
        public String title;
        public boolean deleted;
        public long updatedAt;
    }
}

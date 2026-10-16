package com.jnf.harness.infrastructure.adapter.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jnf.harness.domain.session.meta.ISessionMetaStore;
import com.jnf.harness.domain.session.meta.SessionMeta;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
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
                    json.deleted, json.updatedAt,
                    json.workspaceCode, json.presetCode, json.reasoningEffort));
        } catch (IOException e) {
            throw new UncheckedIOException("读取会话元数据失败: " + file, e);
        }
    }

    @Override
    public List<SessionMeta> findAll() {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<SessionMeta> all = new ArrayList<>();
        try (var dirs = Files.list(root)) {
            for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                String sessionId = dir.getFileName().toString();
                // 单个元数据损坏不应拖垮「列出全部」的调用方（删除工作区解绑）——跳过并继续
                try {
                    find(sessionId).ifPresent(all::add);
                } catch (UncheckedIOException ignored) {
                    // 跳过不可读条目
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("列举会话元数据失败: " + root, e);
        }
        return all;
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
            json.workspaceCode = meta.workspaceCode();
            json.presetCode = meta.presetCode();
            json.reasoningEffort = meta.reasoningEffort();
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
        public String workspaceCode;
        public String presetCode;
        public String reasoningEffort;
    }
}

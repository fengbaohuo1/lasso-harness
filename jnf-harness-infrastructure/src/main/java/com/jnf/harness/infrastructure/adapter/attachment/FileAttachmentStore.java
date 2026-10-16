package com.jnf.harness.infrastructure.adapter.attachment;

import com.jnf.harness.domain.runtime.attachment.IAttachmentStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 文件系统附件库（P11-4）：内容寻址存盘，文件名 = sha256 前 32 位 + 扩展名。
 * <p>相同图片重复粘贴只占一份；引用是纯文件名（不含路径），因此附件目录整体可搬迁。
 */
public class FileAttachmentStore implements IAttachmentStore {

    /** dataRef 前缀，避免把裸路径写进事件。 */
    public static final String SCHEME = "attachment:";

    private static final long DEFAULT_MAX_BYTES = 8L * 1024 * 1024;

    /** 支持的图片类型 → 扩展名（也是白名单，未列出的媒体类型一律拒绝）。 */
    private static final Map<String, String> EXTENSIONS = extensions();

    private final Path root;
    private final long maxBytes;

    public FileAttachmentStore(Path root) {
        this(root, DEFAULT_MAX_BYTES);
    }

    public FileAttachmentStore(Path root, long maxBytes) {
        this.root = root;
        this.maxBytes = maxBytes <= 0 ? DEFAULT_MAX_BYTES : maxBytes;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("附件目录不可创建: " + root, e);
        }
    }

    @Override
    public long maxBytes() {
        return maxBytes;
    }

    @Override
    public String store(String mediaType, byte[] content) {
        String normalized = normalize(mediaType);
        String extension = EXTENSIONS.get(normalized);
        if (extension == null) {
            throw new IllegalArgumentException("不支持的附件类型: " + mediaType);
        }
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("附件内容为空");
        }
        if (content.length > maxBytes) {
            throw new IllegalArgumentException("附件超过大小上限 " + (maxBytes / 1024 / 1024) + "MB");
        }
        String fileName = digest(content) + "." + extension;
        Path target = root.resolve(fileName);
        if (!Files.exists(target)) {
            try {
                Files.write(target, content);
            } catch (IOException e) {
                throw new UncheckedIOException("附件写入失败: " + target, e);
            }
        }
        return SCHEME + fileName;
    }

    @Override
    public Optional<byte[]> read(String dataRef) {
        Path path = resolve(dataRef);
        if (path == null || !Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(path));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean exists(String dataRef) {
        Path path = resolve(dataRef);
        return path != null && Files.isRegularFile(path);
    }

    @Override
    public String toDataUri(String mediaType, String dataRef) {
        Optional<byte[]> bytes = read(dataRef);
        if (bytes.isEmpty()) {
            return null;
        }
        String normalized = mediaType == null || mediaType.isBlank()
                ? guessMediaType(dataRef) : mediaType;
        return "data:" + normalized + ";base64," + Base64.getEncoder().encodeToString(bytes.get());
    }

    /** dataRef → 绝对路径；非本库引用（无前缀 / 越界）返回 null。 */
    private Path resolve(String dataRef) {
        if (dataRef == null || !dataRef.startsWith(SCHEME)) {
            return null;
        }
        String fileName = dataRef.substring(SCHEME.length());
        if (fileName.isBlank() || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            return null;
        }
        return root.resolve(fileName);
    }

    private static String normalize(String mediaType) {
        return mediaType == null ? "" : mediaType.trim().toLowerCase();
    }

    private static String guessMediaType(String dataRef) {
        int dot = dataRef == null ? -1 : dataRef.lastIndexOf('.');
        String extension = dot < 0 ? "" : dataRef.substring(dot + 1).toLowerCase();
        for (Map.Entry<String, String> entry : EXTENSIONS.entrySet()) {
            if (entry.getValue().equals(extension)) {
                return entry.getKey();
            }
        }
        return "application/octet-stream";
    }

    private static String digest(byte[] content) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                builder.append(String.format("%02x", hash[i]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static Map<String, String> extensions() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("image/png", "png");
        map.put("image/jpeg", "jpg");
        map.put("image/jpg", "jpg");
        map.put("image/webp", "webp");
        map.put("image/gif", "gif");
        map.put("image/bmp", "bmp");
        return map;
    }
}

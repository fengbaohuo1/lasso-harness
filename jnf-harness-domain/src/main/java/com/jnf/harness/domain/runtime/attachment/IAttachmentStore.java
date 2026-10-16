package com.jnf.harness.domain.runtime.attachment;

import com.jnf.harness.model.ImageAttachmentRef;

import java.util.Optional;

/**
 * 附件库（P11-4）：二进制内容落盘，事件流里只留 {@link ImageAttachmentRef} 引用。
 * <p>这样做的原因：会话事件是 JSONL 审计日志，把图片 base64 写进事件会让日志膨胀到不可读，
 * 且每次 {@code deriveMessages} 都要搬运整段 base64。引用 + 内容寻址同时天然获得去重能力。
 * <p>{@code dataRef} 形如 {@code attachment:<sha256前32位>.<ext>}，由本端口解释；
 * 适配器无法直接消费它，需经 {@link #toDataUri(String, String)} 还原成 data URI。
 */
public interface IAttachmentStore {

    /** 单张图片大小上限（字节）。 */
    long maxBytes();

    /**
     * 存入附件（内容寻址去重），返回可写入事件的 {@code dataRef}。
     *
     * @throws IllegalArgumentException 媒体类型不支持或超过 {@link #maxBytes()}
     */
    String store(String mediaType, byte[] content);

    /** 按引用读取原始字节（不存在或引用非法返回空）。 */
    Optional<byte[]> read(String dataRef);

    boolean exists(String dataRef);

    /** 转 data URI（{@code data:image/png;base64,...}）；读不到返回 null。 */
    String toDataUri(String mediaType, String dataRef);
}

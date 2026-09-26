package com.jnf.harness.model;

import java.util.Objects;

/**
 * 图片内容块（附件引用）。
 */
public record ImageBlock(ImageAttachmentRef ref) implements ContentBlock {

    public ImageBlock {
        Objects.requireNonNull(ref, "ref");
    }

    public static ImageBlock of(ImageAttachmentRef ref) {
        return new ImageBlock(ref);
    }
}

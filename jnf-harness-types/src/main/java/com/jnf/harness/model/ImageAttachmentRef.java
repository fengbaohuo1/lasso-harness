package com.jnf.harness.model;

import java.util.Objects;

/**
 * 图片附件引用：媒体类型 + 数据引用（data URI 或受控路径，由运行环境解释）。
 */
public record ImageAttachmentRef(String mediaType, String dataRef) {

    public ImageAttachmentRef {
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(dataRef, "dataRef");
    }
}

package com.jnf.harness.model;

/**
 * 消息内容块：一条 {@link Message} 由若干内容块组成（sealed，封闭家族）。
 */
public sealed interface ContentBlock
        permits TextBlock, ReasoningBlock, ImageBlock, ToolCallBlock, ToolResultBlock {
}

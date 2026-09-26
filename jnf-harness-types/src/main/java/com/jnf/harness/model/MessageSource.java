package com.jnf.harness.model;

/**
 * 消息来源。与 LLM 消息角色一一对应。
 */
public enum MessageSource {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}

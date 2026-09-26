package com.jnf.harness.cases.agent;

import com.jnf.harness.cases.agent.node.AgentIntentNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 意图分类验收（第 7 节 DoD：分类表）。
 */
class AgentIntentTest {

    @Test
    @DisplayName("意图分类规则表")
    void classification_table() {
        assertEquals(AgentIntent.TASK_EXECUTION, AgentIntentNode.classify("帮我执行一下构建脚本"));
        assertEquals(AgentIntent.TASK_EXECUTION, AgentIntentNode.classify("删除临时目录"));
        assertEquals(AgentIntent.CODE_QUESTION, AgentIntentNode.classify("这个函数为什么报错"));
        assertEquals(AgentIntent.CODE_QUESTION, AgentIntentNode.classify("接口的实现原理是什么"));
        assertEquals(AgentIntent.CLARIFICATION, AgentIntentNode.classify("用 Java 还是 Go？"));
        assertEquals(AgentIntent.CHAT, AgentIntentNode.classify("你好"));
        assertEquals(AgentIntent.CHAT, AgentIntentNode.classify("今天天气不错"));
    }
}

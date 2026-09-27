package com.jnf.harness.domain.llm.service;

import com.jnf.harness.domain.llm.model.valobj.FinishReason;
import com.jnf.harness.domain.llm.model.valobj.StreamChunk;
import com.jnf.harness.domain.llm.model.valobj.TokenUsage;
import com.jnf.harness.model.ToolCallBlock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 流式装配（真实厂商抓包回归）：工具调用按 callId 归并、名称补填、无名残块过滤。
 */
class BlockAssemblerTest {

    @Test
    @DisplayName("名称后到补填；已有名称不被空串覆盖")
    void name_backfills_when_arriving_late() {
        BlockAssembler assembler = new BlockAssembler();
        assembler.accept(new StreamChunk.ToolCallDelta("c1", "", "{\"a\":1}"));
        assembler.accept(new StreamChunk.ToolCallDelta("c1", "fs_read", ""));
        assembler.accept(new StreamChunk.ToolCallDelta("c1", "", "}"));
        assembler.accept(new StreamChunk.DoneChunk(FinishReason.TOOL_CALLS, TokenUsage.zero()));

        List<ToolCallBlock> calls = assembler.result().toolCalls();
        assertEquals(1, calls.size());
        assertEquals("fs_read", calls.get(0).name());
    }

    @Test
    @DisplayName("无名残块被过滤，不进入回放")
    void nameless_blocks_dropped() {
        BlockAssembler assembler = new BlockAssembler();
        assembler.accept(new StreamChunk.ToolCallDelta("c1", "", "{}"));
        assembler.accept(new StreamChunk.ToolCallDelta("c2", "todo_write", "{}"));
        assembler.accept(new StreamChunk.DoneChunk(FinishReason.TOOL_CALLS, TokenUsage.zero()));

        List<ToolCallBlock> calls = assembler.result().toolCalls();
        assertEquals(1, calls.size());
        assertEquals("todo_write", calls.get(0).name());
    }
}

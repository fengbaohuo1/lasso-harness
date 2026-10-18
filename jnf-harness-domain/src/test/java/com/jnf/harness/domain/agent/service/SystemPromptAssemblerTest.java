package com.jnf.harness.domain.agent.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 系统提示词装配：order 排序、动态段落（技能目录）按 cwd 现算、异常隔离。
 */
class SystemPromptAssemblerTest {

    @Test
    @DisplayName("静态段落按 order 拼接，空段落被过滤")
    void assembles_static_sections_in_order() {
        SystemPromptAssembler assembler = new SystemPromptAssembler();
        assembler.registerSection(new SystemPromptAssembler.PromptSection("late", 20, "LATE"));
        assembler.registerSection(new SystemPromptAssembler.PromptSection("base", 0, "BASE"));
        assembler.registerSection(new SystemPromptAssembler.PromptSection("blank", 10, "   "));

        assertEquals("BASE\n\nLATE", assembler.assemble());
    }

    @Test
    @DisplayName("动态段落按 cwd 现算，与静态段落按 order 合并")
    void dynamic_section_renders_with_cwd() {
        SystemPromptAssembler assembler = new SystemPromptAssembler();
        assembler.registerSection(new SystemPromptAssembler.PromptSection("base", 0, "BASE"));
        assembler.registerDynamicSection(new SystemPromptAssembler.DynamicSection(
                "skills", 15, cwd -> cwd.isEmpty() ? "" : "SKILLS@" + cwd));
        assembler.registerSection(new SystemPromptAssembler.PromptSection("tail", 30, "TAIL"));

        assertEquals("BASE\n\nSKILLS@/w\n\nTAIL", assembler.assemble("/w"));
        assertEquals("BASE\n\nTAIL", assembler.assemble(""));
    }

    @Test
    @DisplayName("同 order 时静态段落在前；动态段异常按空处理")
    void dynamic_section_failures_are_isolated() {
        SystemPromptAssembler assembler = new SystemPromptAssembler();
        assembler.registerSection(new SystemPromptAssembler.PromptSection("static", 5, "STATIC"));
        assembler.registerDynamicSection(new SystemPromptAssembler.DynamicSection(
                "dynamic", 5, cwd -> "DYNAMIC"));
        assembler.registerDynamicSection(new SystemPromptAssembler.DynamicSection(
                "broken", 6, cwd -> {
                    throw new IllegalStateException("boom");
                }));

        assertEquals("STATIC\n\nDYNAMIC", assembler.assemble("."));
    }

    @Test
    @DisplayName("同名段落重复注册覆盖旧值，remove 后消失")
    void register_replaces_and_remove_deletes() {
        SystemPromptAssembler assembler = new SystemPromptAssembler();
        assembler.registerSection(new SystemPromptAssembler.PromptSection("a", 0, "OLD"));
        assembler.registerSection(new SystemPromptAssembler.PromptSection("a", 0, "NEW"));
        assertEquals("NEW", assembler.assemble());

        assembler.removeSection("a");
        assertEquals("", assembler.assemble());
        assertFalse(assembler.assemble().contains("NEW"));
        assertTrue(assembler.assemble(".").isEmpty());
    }
}

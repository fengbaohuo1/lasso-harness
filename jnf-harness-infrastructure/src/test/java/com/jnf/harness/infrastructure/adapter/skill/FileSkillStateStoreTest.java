package com.jnf.harness.infrastructure.adapter.skill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文件技能启用态：默认启用、禁用落盘、跨实例读回、remove 遗忘。
 */
class FileSkillStateStoreTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("状态文件不存在时全部技能默认启用，且不会主动创建文件")
    void defaults_to_enabled() {
        Path file = tempDir.resolve("state").resolve("skills.properties");
        FileSkillStateStore store = new FileSkillStateStore(file);

        assertTrue(store.isEnabled("demo"));
        assertEquals(Set.of(), store.disabled());
        assertFalse(Files.exists(file), "只读访问不应创建状态文件");
    }

    @Test
    @DisplayName("禁用后落盘（自动建父目录），新实例仍读回禁用态；重新启用后也持久化")
    void disabled_state_persists_across_instances() {
        Path file = tempDir.resolve("state").resolve("skills.properties");
        FileSkillStateStore store = new FileSkillStateStore(file);

        store.setEnabled("demo", false);
        assertFalse(store.isEnabled("demo"));
        assertTrue(Files.isRegularFile(file), "写变更应自动创建父目录和状态文件");

        FileSkillStateStore reopened = new FileSkillStateStore(file);
        assertFalse(reopened.isEnabled("demo"));
        assertEquals(Set.of("demo"), reopened.disabled());

        reopened.setEnabled("demo", true);
        FileSkillStateStore reopenedAgain = new FileSkillStateStore(file);
        assertTrue(reopenedAgain.isEnabled("demo"));
        assertEquals(Set.of(), reopenedAgain.disabled());
    }

    @Test
    @DisplayName("remove 遗忘启用态，恢复默认启用，其他技能状态不受影响")
    void remove_forgets_state() {
        Path file = tempDir.resolve("skills.properties");
        FileSkillStateStore store = new FileSkillStateStore(file);
        store.setEnabled("alpha", false);
        store.setEnabled("beta", false);
        assertEquals(Set.of("alpha", "beta"), store.disabled());

        store.remove("alpha");

        FileSkillStateStore reopened = new FileSkillStateStore(file);
        assertTrue(reopened.isEnabled("alpha"));
        assertFalse(reopened.isEnabled("beta"));
        assertEquals(Set.of("beta"), reopened.disabled());
    }
}

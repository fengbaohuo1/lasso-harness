package com.jnf.harness.infrastructure.adapter.shell;

import com.jnf.harness.domain.tool.shell.ShellResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地 shell 执行器（M8 #6）：Windows 控制台强制 UTF-8，中文输出不乱码。
 */
class LocalShellExecutorTest {

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @DisplayName("Windows：echo 中文输出不乱码（chcp 65001 + UTF-8 解码）")
    void windows_console_utf8_roundtrip() {
        ShellResult result = new LocalShellExecutor()
                .execute("echo 中文输出测试", Path.of("."), 30);
        assertTrue(result.output().contains("中文输出测试"),
                "输出应含中文原文，实际: " + result.output());
        assertFalse(result.output().contains("��") || result.output().contains("◆"),
                "不应出现乱码字符: " + result.output());
    }
}

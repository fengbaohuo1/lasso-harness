package com.jnf.harness.domain.terminal;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 终端服务（terminal 域）：会话式 shell（持久进程 + 输出缓冲 + 轮询读取）。
 */
public class TerminalService {

    /** 终端会话快照。 */
    public record TerminalSessionSnapshot(String sessionId, String status, String output) {
    }

    private static final class Session {
        final Process process;
        final OutputStream stdin;
        final StringBuilder output = new StringBuilder();

        Session(Process process) {
            this.process = process;
            this.stdin = process.getOutputStream();
        }
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    /** 打开终端会话。 */
    public String open() {
        String sessionId = UUID.randomUUID().toString();
        try {
            boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
            // 持久交互 shell：命令经 stdin 逐行喂入，输出进缓冲
            // cmd 内置命令输出编码在 cmd.exe 启动时固化（chcp 对同进程无效）：
            // 外层 chcp 65001 设码页，内层新交互 cmd → 输出 UTF-8，与 pump 解码一致
            Process process = new ProcessBuilder(windows
                    ? List.of("cmd", "/c", "chcp 65001 >nul & cmd.exe")
                    : List.of("/bin/sh"))
                    .redirectErrorStream(true)
                    .start();
            Session session = new Session(process);
            sessions.put(sessionId, session);
            Thread reader = new Thread(() -> pump(session), "terminal-reader-" + sessionId);
            reader.setDaemon(true);
            reader.start();
            return sessionId;
        } catch (Exception e) {
            throw new IllegalStateException("终端打开失败: " + e.getMessage(), e);
        }
    }

    private void pump(Session session) {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(session.process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                synchronized (session.output) {
                    session.output.append(line).append('\n');
                }
            }
        } catch (Exception ignored) {
            // 进程退出
        }
    }

    /** 发送命令（追加换行）。 */
    public void send(String sessionId, String command) {
        Session session = require(sessionId);
        try {
            session.stdin.write((command + "\n").getBytes(StandardCharsets.UTF_8));
            session.stdin.flush();
        } catch (Exception e) {
            throw new IllegalStateException("终端写入失败: " + e.getMessage(), e);
        }
    }

    /** 读取输出（自 offset 起的全部内容）。 */
    public TerminalSessionSnapshot read(String sessionId) {
        Session session = require(sessionId);
        String text;
        synchronized (session.output) {
            text = session.output.toString();
        }
        boolean alive = session.process.isAlive();
        return new TerminalSessionSnapshot(sessionId, alive ? "OPEN" : "CLOSED", text);
    }

    public void close(String sessionId) {
        Session session = sessions.remove(sessionId);
        if (session != null) {
            session.process.destroyForcibly();
        }
    }

    private Session require(String sessionId) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("终端会话不存在: " + sessionId);
        }
        return session;
    }
}

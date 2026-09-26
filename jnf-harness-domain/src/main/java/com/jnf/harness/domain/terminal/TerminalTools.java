package com.jnf.harness.domain.terminal;

import com.jnf.harness.model.AbstractTool;
import com.jnf.harness.model.ToolDefinition;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * terminal 工具族：terminal_open / terminal_send / terminal_read。
 */
public final class TerminalTools {

    private TerminalTools() {
    }

    public static List<ToolDefinition> all(TerminalService service) {
        return List.of(new OpenTool(service), new SendTool(service), new ReadTool(service));
    }

    static class OpenTool extends AbstractTool {
        private final TerminalService service;

        OpenTool(TerminalService service) {
            this.service = service;
        }

        @Override
        public String name() {
            return "terminal_open";
        }

        @Override
        public String description() {
            return "打开持久终端会话，返回 session_id。";
        }

        @Override
        public Map<String, Object> parameters() {
            return objectSchema().build();
        }

        @Override
        protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok("session_id=" + service.open());
        }
    }

    static class SendTool extends AbstractTool {
        private final TerminalService service;

        SendTool(TerminalService service) {
            this.service = service;
        }

        @Override
        public String name() {
            return "terminal_send";
        }

        @Override
        public String description() {
            return "向终端会话发送命令。";
        }

        @Override
        public Map<String, Object> parameters() {
            return objectSchema().prop("session_id", stringSchema("会话 id"))
                    .prop("command", stringSchema("命令")).required("session_id", "command").build();
        }

        @Override
        protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            service.send(str(args, "session_id"), str(args, "command"));
            return ok("已发送");
        }
    }

    static class ReadTool extends AbstractTool {
        private final TerminalService service;

        ReadTool(TerminalService service) {
            this.service = service;
        }

        @Override
        public String name() {
            return "terminal_read";
        }

        @Override
        public String description() {
            return "读取终端会话输出。";
        }

        @Override
        public Map<String, Object> parameters() {
            return objectSchema().prop("session_id", stringSchema("会话 id")).required("session_id").build();
        }

        @Override
        protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            try {
                TerminalService.TerminalSessionSnapshot snapshot = service.read(str(args, "session_id"));
                String output = snapshot.output();
                return ok("status=" + snapshot.status() + "\n"
                        + (output.length() > 8000 ? output.substring(0, 8000) + "\n...（截断）" : output));
            } catch (Exception e) {
                return fail(e.getMessage(), "TERMINAL_ERROR");
            }
        }
    }
}

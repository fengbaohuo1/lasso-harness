package com.jnf.harness.domain.schedule;

import com.jnf.harness.model.AbstractTool;
import com.jnf.harness.model.ToolDefinition;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * schedule 工具族：schedule_create / schedule_list。
 */
public final class ScheduleTools {

    private ScheduleTools() {
    }

    public static List<ToolDefinition> all(ScheduleService service) {
        return List.of(new CreateTool(service), new ListTool(service));
    }

    static class CreateTool extends AbstractTool {
        private final ScheduleService service;

        CreateTool(ScheduleService service) {
            this.service = service;
        }

        @Override
        public String name() {
            return "schedule_create";
        }

        @Override
        public String description() {
            return "创建调度记录（kind=INTERVAL/CRON，spec 如 5m）。";
        }

        @Override
        public Map<String, Object> parameters() {
            return objectSchema().prop("name", stringSchema("调度名"))
                    .prop("kind", stringSchema("INTERVAL/CRON"))
                    .prop("spec", stringSchema("如 5m / 0 * * * *"))
                    .required("name", "spec").build();
        }

        @Override
        protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            ScheduleService.ScheduleRecord record = service.create(
                    str(args, "kind"), str(args, "name"), str(args, "spec"));
            return ok("schedule_id=" + record.scheduleId() + " state=" + record.state());
        }
    }

    static class ListTool extends AbstractTool {
        private final ScheduleService service;

        ListTool(ScheduleService service) {
            this.service = service;
        }

        @Override
        public String name() {
            return "schedule_list";
        }

        @Override
        public String description() {
            return "列出调度记录。";
        }

        @Override
        public Map<String, Object> parameters() {
            return objectSchema().build();
        }

        @Override
        protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            StringBuilder sb = new StringBuilder();
            service.list().forEach(s -> sb.append(s.scheduleId()).append(" | ")
                    .append(s.name()).append(" | ").append(s.spec()).append(" | ")
                    .append(s.state()).append('\n'));
            return ok(sb.length() == 0 ? "（无调度）" : sb.toString());
        }
    }
}

package com.jnf.harness.domain.jobs;

import com.jnf.harness.model.AbstractTool;
import com.jnf.harness.model.ToolDefinition;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * jobs 工具族：job_run / job_list / job_output / job_kill。
 */
public final class JobTools {

    private JobTools() {
    }

    public static List<ToolDefinition> all(JobRegistryService service) {
        return List.of(new RunTool(service), new ListTool(service),
                new OutputTool(service), new KillTool(service));
    }

    private static Path cwdOf(ToolRunContext ctx) {
        return Path.of(ctx.cwd()).toAbsolutePath();
    }

    static class RunTool extends AbstractTool {
        private final JobRegistryService service;

        RunTool(JobRegistryService service) {
            this.service = service;
        }

        @Override
        public String name() {
            return "job_run";
        }

        @Override
        public String description() {
            return "后台异步运行命令，立即返回 job_id（用 job_output 取结果）。";
        }

        @Override
        public Map<String, Object> parameters() {
            return objectSchema().prop("name", stringSchema("作业名"))
                    .prop("command", stringSchema("命令")).required("command").build();
        }

        @Override
        protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            JobRegistryService.JobRecord record = service.start(
                    str(args, "name"), str(args, "command"), cwdOf(ctx));
            return ok("job_id=" + record.jobId() + " status=" + record.status());
        }
    }

    static class ListTool extends AbstractTool {
        private final JobRegistryService service;

        ListTool(JobRegistryService service) {
            this.service = service;
        }

        @Override
        public String name() {
            return "job_list";
        }

        @Override
        public String description() {
            return "列出后台作业（id/name/status）。";
        }

        @Override
        public Map<String, Object> parameters() {
            return objectSchema().build();
        }

        @Override
        protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            StringBuilder sb = new StringBuilder();
            service.list().forEach(job -> sb.append(job.jobId()).append(" | ")
                    .append(job.name()).append(" | ").append(job.status()).append('\n'));
            return ok(sb.length() == 0 ? "（无作业）" : sb.toString());
        }
    }

    static class OutputTool extends AbstractTool {
        private final JobRegistryService service;

        OutputTool(JobRegistryService service) {
            this.service = service;
        }

        @Override
        public String name() {
            return "job_output";
        }

        @Override
        public String description() {
            return "读取作业输出与状态。";
        }

        @Override
        public Map<String, Object> parameters() {
            return objectSchema().prop("job_id", stringSchema("作业 id")).required("job_id").build();
        }

        @Override
        protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return service.get(str(args, "job_id"))
                    .map(job -> ok("status=" + job.status() + "\n" + truncate(job.output())))
                    .orElse(fail("作业不存在", "JOB_NOT_FOUND"));
        }

        private String truncate(String output) {
            return output != null && output.length() > 8000
                    ? output.substring(0, 8000) + "\n...（截断）" : String.valueOf(output);
        }
    }

    static class KillTool extends AbstractTool {
        private final JobRegistryService service;

        KillTool(JobRegistryService service) {
            this.service = service;
        }

        @Override
        public String name() {
            return "job_kill";
        }

        @Override
        public String description() {
            return "终止后台作业。";
        }

        @Override
        public Map<String, Object> parameters() {
            return objectSchema().prop("job_id", stringSchema("作业 id")).required("job_id").build();
        }

        @Override
        protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            service.kill(str(args, "job_id"));
            return ok("已请求终止");
        }
    }
}

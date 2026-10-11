package com.jnf.harness.app;

import com.jnf.harness.domain.goal.GoalService;
import com.jnf.harness.domain.jobs.JobRegistryService;
import com.jnf.harness.domain.schedule.ScheduleService;
import com.jnf.harness.domain.tool.service.ToolRegistry;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第 12a 批验收：jobs（R7 持久化）/ schedule / goal / plan 工具族。
 */
@SpringBootTest(classes = JnfHarnessApplication.class,
        properties = {"spring.datasource.url=jdbc:h2:mem:rttest;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"})
class RuntimeCapabilitiesIntegrationTest {

    @Autowired
    JobRegistryService jobService;

    @Autowired
    ScheduleService scheduleService;

    @Autowired
    GoalService goalService;


    @Autowired
    ToolRegistry toolRegistry;

    private final ToolRunContext ctx = new ToolRunContext() {
        @Override
        public String agentId() {
            return "a";
        }

        @Override
        public String sessionId() {
            return "rt-session";
        }

        @Override
        public String cwd() {
            return ".";
        }
    };

    @Test
    @DisplayName("jobs：异步执行 + 输出持久化（R7——仓储可跨服务实例读到）")
    void jobs_persist_across_restart() throws Exception {
        JobRegistryService.JobRecord started = jobService.start("echo-job", "echo hello-job",
                java.nio.file.Path.of(".").toAbsolutePath());
        String jobId = started.jobId();

        JobRegistryService.JobRecord finished = null;
        for (int i = 0; i < 100; i++) {
            finished = jobService.get(jobId).orElseThrow();
            if (!"RUNNING".equals(finished.status())) {
                break;
            }
            Thread.sleep(20);
        }
        assertEquals("COMPLETED", finished.status());
        assertTrue(finished.output().contains("hello-job"));

        // R7 语义：换一个服务实例（模拟重启）仍能从仓储读到记录
        JobRegistryService restarted = new JobRegistryService(
                new com.jnf.harness.infrastructure.adapter.setting.RuntimeStateRepositories.JobRepository(
                        new org.springframework.jdbc.core.JdbcTemplate(
                                new org.springframework.jdbc.datasource.DriverManagerDataSource(
                                        "jdbc:h2:mem:rttest;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                                        "sa", ""))),
                new com.jnf.harness.infrastructure.adapter.shell.LocalShellExecutor());
        assertTrue(restarted.get(jobId).isPresent(), "重启后作业记录必须还在（R7）");
    }

    @Test
    @DisplayName("schedule：创建/取消/列表持久化；goal：快照更新；plan：exit_plan_mode 工具")
    void schedule_goal_plan() throws Exception {
        ScheduleService.ScheduleRecord record = scheduleService.create("INTERVAL", "nightly", "5m");
        assertFalse(scheduleService.list().isEmpty());
        assertEquals("CANCELED", scheduleService.cancel(record.scheduleId()).state());

        GoalService.GoalSnapshot snapshot = goalService.update("rt-session",
                "完成第一阶段", "复刻 dsh-java", "ACTIVE", 50);
        assertEquals("ACTIVE", goalService.get("rt-session").status());
        assertEquals(1, snapshot.revision());
        assertTrue(goalService.tick("rt-session").roundsStarted() == 1);

        ToolExecutionResult plan = toolRegistry.lookup("exit_plan_mode").orElseThrow()
                .execute(Map.of("plan", "分节施工"), ctx).get();
        assertTrue(plan.success());
        // plan 态属插件内部（A3 外移）：工具成功即契约
    }
}

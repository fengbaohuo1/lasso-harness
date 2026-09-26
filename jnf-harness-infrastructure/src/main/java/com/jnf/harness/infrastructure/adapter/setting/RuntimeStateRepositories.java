package com.jnf.harness.infrastructure.adapter.setting;

import com.jnf.harness.domain.goal.GoalService;
import com.jnf.harness.domain.jobs.JobRegistryService;
import com.jnf.harness.domain.schedule.ScheduleService;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * goal / jobs / schedule 三仓储（JdbcTemplate 实现，R7 持久化）。
 */
public class RuntimeStateRepositories {

    private RuntimeStateRepositories() {
    }

    /** 目标仓储 → harness_goal_state。 */
    public static class GoalRepository implements GoalService.IGoalRepository {

        private final JdbcTemplate jdbc;

        public GoalRepository(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public void save(GoalService.GoalSnapshot snapshot) {
            jdbc.update("INSERT INTO harness_goal_state (session_id, goal_id, revision, title, description, "
                            + "status, blocked_reason, max_rounds, rounds_started, updated_at) VALUES (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP) "
                            + "ON DUPLICATE KEY UPDATE goal_id = VALUES(goal_id), revision = VALUES(revision), "
                            + "title = VALUES(title), description = VALUES(description), status = VALUES(status), "
                            + "blocked_reason = VALUES(blocked_reason), max_rounds = VALUES(max_rounds), "
                            + "rounds_started = VALUES(rounds_started), updated_at = CURRENT_TIMESTAMP",
                    snapshot.sessionId(), snapshot.goalId(), snapshot.revision(), snapshot.title(),
                    snapshot.description(), snapshot.status(), snapshot.blockedReason(),
                    snapshot.maxRounds(), snapshot.roundsStarted());
        }

        @Override
        public GoalService.GoalSnapshot findBySession(String sessionId) {
            List<GoalService.GoalSnapshot> rows = jdbc.query(
                    "SELECT * FROM harness_goal_state WHERE session_id = ?", (rs, i) ->
                            new GoalService.GoalSnapshot(rs.getString("session_id"), rs.getString("goal_id"),
                                    rs.getLong("revision"), rs.getString("title"), rs.getString("description"),
                                    rs.getString("status"), rs.getString("blocked_reason"),
                                    rs.getInt("max_rounds"), rs.getInt("rounds_started")), sessionId);
            return rows.isEmpty() ? null : rows.get(0);
        }
    }

    /** 作业仓储 → harness_job。 */
    public static class JobRepository implements JobRegistryService.IJobRegistryRepository {

        private final JdbcTemplate jdbc;

        public JobRepository(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public void save(JobRegistryService.JobRecord record) {
            jdbc.update("INSERT INTO harness_job (job_id, name, command, status, output, created_at, finished_at) "
                            + "VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE name = VALUES(name), "
                            + "command = VALUES(command), status = VALUES(status), output = VALUES(output), "
                            + "finished_at = VALUES(finished_at)",
                    record.jobId(), record.name(), record.command(), record.status(), record.output(),
                    toTimestamp(record.createdAt()), toTimestamp(record.finishedAt()));
        }

        @Override
        public Optional<JobRegistryService.JobRecord> findById(String jobId) {
            List<JobRegistryService.JobRecord> rows = jdbc.query(
                    "SELECT * FROM harness_job WHERE job_id = ?", (rs, i) -> map(rs), jobId);
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
        }

        @Override
        public List<JobRegistryService.JobRecord> findAll() {
            return jdbc.query("SELECT * FROM harness_job ORDER BY created_at", (rs, i) -> map(rs));
        }

        private JobRegistryService.JobRecord map(java.sql.ResultSet rs) throws java.sql.SQLException {
            return new JobRegistryService.JobRecord(rs.getString("job_id"), rs.getString("name"),
                    rs.getString("command"), rs.getString("status"), rs.getString("output"),
                    toInstant(rs.getTimestamp("created_at")), toInstant(rs.getTimestamp("finished_at")));
        }
    }

    /** 调度仓储 → harness_schedule。 */
    public static class ScheduleRepository implements ScheduleService.IScheduleRepository {

        private final JdbcTemplate jdbc;

        public ScheduleRepository(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public void save(ScheduleService.ScheduleRecord record) {
            jdbc.update("INSERT INTO harness_schedule (schedule_id, kind, name, spec, state, created_at) "
                            + "VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE kind = VALUES(kind), "
                            + "name = VALUES(name), spec = VALUES(spec), state = VALUES(state)",
                    record.scheduleId(), record.kind(), record.name(), record.spec(), record.state(),
                    toTimestamp(record.createdAt()));
        }

        @Override
        public Optional<ScheduleService.ScheduleRecord> findById(String scheduleId) {
            List<ScheduleService.ScheduleRecord> rows = jdbc.query(
                    "SELECT * FROM harness_schedule WHERE schedule_id = ?", (rs, i) ->
                            new ScheduleService.ScheduleRecord(rs.getString("schedule_id"), rs.getString("kind"),
                                    rs.getString("name"), rs.getString("spec"), rs.getString("state"),
                                    toInstant(rs.getTimestamp("created_at"))), scheduleId);
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
        }

        @Override
        public List<ScheduleService.ScheduleRecord> findAll() {
            return jdbc.query("SELECT * FROM harness_schedule ORDER BY created_at", (rs, i) ->
                    new ScheduleService.ScheduleRecord(rs.getString("schedule_id"), rs.getString("kind"),
                            rs.getString("name"), rs.getString("spec"), rs.getString("state"),
                            toInstant(rs.getTimestamp("created_at"))));
        }
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}

package com.jnf.harness.domain.jobs;

import com.jnf.harness.domain.tool.shell.IShellExecutor;
import com.jnf.harness.domain.tool.shell.ShellResult;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 后台作业服务（jobs 域，修正项 R7：记录持久化，重启不丢）。
 */
public class JobRegistryService {

    /** 作业记录。 */
    public record JobRecord(String jobId, String name, String command, String status,
                            String output, Instant createdAt, Instant finishedAt) {
    }

    /** 作业仓储端口。 */
    public interface IJobRegistryRepository {
        void save(JobRecord record);

        Optional<JobRecord> findById(String jobId);

        List<JobRecord> findAll();
    }

    private final IJobRegistryRepository repository;
    private final IShellExecutor shellExecutor;
    private final Map<String, JobRecord> running = new ConcurrentHashMap<>();
    private final ExecutorService workers = Executors.newFixedThreadPool(2, r -> {
        Thread thread = new Thread(r, "job-worker");
        thread.setDaemon(true);
        return thread;
    });

    public JobRegistryService(IJobRegistryRepository repository, IShellExecutor shellExecutor) {
        this.repository = repository;
        this.shellExecutor = shellExecutor;
    }

    /** 启动作业（异步执行命令，记录全程落库）。 */
    public JobRecord start(String name, String command, Path cwd) {
        String jobId = UUID.randomUUID().toString();
        JobRecord created = new JobRecord(jobId, name, command, "RUNNING", "", Instant.now(), null);
        running.put(jobId, created);
        repository.save(created);
        workers.submit(() -> {
            ShellResult result = shellExecutor.execute(command, cwd, 300);
            JobRecord finished = new JobRecord(jobId, name, command,
                    result.timedOut() ? "TIMEOUT" : (result.exitCode() == 0 ? "COMPLETED" : "FAILED"),
                    result.output(), created.createdAt(), Instant.now());
            running.remove(jobId);
            repository.save(finished);
        });
        return created;
    }

    public Optional<JobRecord> get(String jobId) {
        JobRecord live = running.get(jobId);
        return live != null ? Optional.of(live) : repository.findById(jobId);
    }

    public List<JobRecord> list() {
        List<JobRecord> all = new ArrayList<>(repository.findAll());
        return all;
    }

    /** 终止（尽力：标记取消；进程级 kill 随后接）。 */
    public void kill(String jobId) {
        JobRecord record = get(jobId).orElse(null);
        if (record != null && "RUNNING".equals(record.status())) {
            JobRecord killed = new JobRecord(record.jobId(), record.name(), record.command(),
                    "CANCELED", record.output(), record.createdAt(), Instant.now());
            running.remove(jobId);
            repository.save(killed);
        }
    }
}

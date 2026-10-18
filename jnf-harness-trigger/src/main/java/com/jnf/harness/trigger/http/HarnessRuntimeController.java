package com.jnf.harness.trigger.http;

import com.jnf.harness.api.response.Response;
import com.jnf.harness.domain.extension.ExtensionManagementService;
import com.jnf.harness.domain.skill.SkillAdminService;
import com.jnf.harness.domain.terminal.TerminalService;
import com.jnf.harness.domain.workflow.WorkflowService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 运行时周边 REST：workflow / terminal / extensions。
 */
@RestController
@RequestMapping("/api/harness")
public class HarnessRuntimeController {

    private final WorkflowService workflowService;
    private final TerminalService terminalService;
    private final ExtensionManagementService extensionService;
    private final SkillAdminService skillAdminService;

    public HarnessRuntimeController(WorkflowService workflowService,
                                    TerminalService terminalService,
                                    ExtensionManagementService extensionService,
                                    SkillAdminService skillAdminService) {
        this.workflowService = workflowService;
        this.terminalService = terminalService;
        this.extensionService = extensionService;
        this.skillAdminService = skillAdminService;
    }

    // ---- workflow ----

    @PostMapping("/workflow/start")
    public Response<WorkflowService.WorkflowRun> startWorkflow(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> steps = body.get("steps") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList() : List.of();
        return Response.success(workflowService.start(steps));
    }

    @GetMapping("/workflow/{runId}")
    public Response<WorkflowService.WorkflowRun> workflowStatus(@PathVariable String runId) {
        return Response.success(workflowService.get(runId));
    }

    @PostMapping("/workflow/{runId}/cancel")
    public Response<Void> cancelWorkflow(@PathVariable String runId) {
        workflowService.cancel(runId);
        return Response.success(null);
    }

    // ---- terminal ----

    @PostMapping("/terminal/sessions")
    public Response<Map<String, Object>> openTerminal() {
        return Response.success(Map.of("sessionId", terminalService.open(), "status", "OPEN"));
    }

    @PostMapping("/terminal/sessions/{sessionId}/send")
    public Response<Void> terminalSend(@PathVariable String sessionId, @RequestBody Map<String, Object> body) {
        terminalService.send(sessionId, String.valueOf(body.getOrDefault("command", "")));
        return Response.success(null);
    }

    @GetMapping("/terminal/sessions/{sessionId}/read")
    public Response<TerminalService.TerminalSessionSnapshot> terminalRead(@PathVariable String sessionId) {
        return Response.success(terminalService.read(sessionId));
    }

    // ---- extensions ----

    @GetMapping("/extensions")
    public Response<Map<String, Object>> extensions() {
        String workdir = Path.of("").toAbsolutePath().toString();
        return Response.success(Map.of(
                "skills", skillAdminService.list(workdir),
                "mcpServers", extensionService.listMcpServers(),
                "cli", extensionService.cliConfig()));
    }
}

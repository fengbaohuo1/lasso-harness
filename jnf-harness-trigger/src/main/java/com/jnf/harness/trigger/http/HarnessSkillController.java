package com.jnf.harness.trigger.http;

import com.jnf.harness.api.response.Response;
import com.jnf.harness.domain.skill.ISkillService;
import com.jnf.harness.domain.skill.SkillAdminService;
import com.jnf.harness.domain.skill.SkillDefinition;
import com.jnf.harness.domain.skill.SkillInstallResult;
import com.jnf.harness.domain.skill.SkillSummary;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.List;

/**
 * 技能包（SKILL.md）管理 REST：列表 / 详情 / 安装 / 启停 / 删除。
 * <p>技能发现根目录：项目级 {@code <cwd>/.jnf/skills} &gt; {@code harness.skills.roots} &gt; 用户级
 * {@code ~/.jnf/skills}；cwd 缺省为服务进程工作目录。
 */
@RestController
@RequestMapping("/api/harness/skills")
public class HarnessSkillController {

    private final SkillAdminService adminService;
    private final ISkillService skillService;

    public HarnessSkillController(SkillAdminService adminService, ISkillService skillService) {
        this.adminService = adminService;
        this.skillService = skillService;
    }

    @GetMapping
    public Response<List<SkillView>> list(@RequestParam(required = false) String cwd) {
        return Response.success(adminService.list(workdir(cwd)).stream().map(SkillView::of).toList());
    }

    @GetMapping("/{name}")
    public Response<SkillDetail> detail(@PathVariable String name,
                                        @RequestParam(required = false) String cwd) {
        return adminService.get(name, workdir(cwd))
                .<Response<SkillDetail>>map(definition ->
                        Response.success(SkillDetail.of(definition, skillService.isEnabled(name))))
                .orElseGet(() -> Response.invalidArgument("技能不存在: " + name));
    }

    @PostMapping("/install")
    public Response<SkillInstallResult> install(@RequestBody(required = false) SkillInstallRequest request) {
        if (request == null) {
            return Response.invalidArgument("请求体不能为空");
        }
        SkillInstallResult result;
        if (request.gitUrl() != null && !request.gitUrl().isBlank()) {
            result = adminService.installFromGit(request.gitUrl(), request.subdir(), request.name());
        } else if (request.zipPath() != null && !request.zipPath().isBlank()) {
            result = adminService.installFromZip(Path.of(request.zipPath()), request.subdir(), request.name());
        } else {
            result = SkillInstallResult.fail("gitUrl 与 zipPath 至少提供一个");
        }
        if (!result.success()) {
            return Response.invalidArgument(result.message());
        }
        return Response.success(result);
    }

    @PostMapping("/{name}/enable")
    public Response<SkillView> enable(@PathVariable String name,
                                      @RequestParam(required = false) String cwd) {
        return toggle(name, cwd, true);
    }

    @PostMapping("/{name}/disable")
    public Response<SkillView> disable(@PathVariable String name,
                                       @RequestParam(required = false) String cwd) {
        return toggle(name, cwd, false);
    }

    @DeleteMapping("/{name}")
    public Response<Void> remove(@PathVariable String name) {
        try {
            adminService.remove(name);
            return Response.success(null);
        } catch (IllegalArgumentException e) {
            return Response.invalidArgument(e.getMessage());
        }
    }

    private Response<SkillView> toggle(String name, String cwd, boolean enabled) {
        try {
            return Response.success(SkillView.of(adminService.setEnabled(name, workdir(cwd), enabled)));
        } catch (IllegalArgumentException e) {
            return Response.invalidArgument(e.getMessage());
        }
    }

    private static String workdir(String cwd) {
        return cwd == null || cwd.isBlank() ? Path.of("").toAbsolutePath().toString() : cwd;
    }

    /** 技能摘要（管理面）。 */
    public record SkillView(String name, String description, String whenToUse,
                            String source, String path, boolean enabled) {
        static SkillView of(SkillSummary summary) {
            return new SkillView(summary.name(), summary.description(), summary.whenToUse(),
                    summary.source().name(), summary.path(), summary.enabled());
        }
    }

    /** 技能详情（含正文）。 */
    public record SkillDetail(String name, String description, String whenToUse,
                              String source, String path, boolean enabled, String content) {
        static SkillDetail of(SkillDefinition definition, boolean enabled) {
            SkillSummary summary = new SkillSummary(definition.name(),
                    definition.candidate().description(), definition.candidate().whenToUse(),
                    definition.source(), definition.candidate().path(), enabled);
            return new SkillDetail(summary.name(), summary.description(), summary.whenToUse(),
                    summary.source().name(), summary.path(), enabled, definition.body());
        }
    }

    /** 安装请求：Git URL 或本地 zip 二选一。 */
    public record SkillInstallRequest(String gitUrl, String zipPath, String subdir, String name) {
    }
}

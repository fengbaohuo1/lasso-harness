package com.jnf.harness.trigger.http;

import com.jnf.harness.api.response.Response;
import com.jnf.harness.domain.runtime.workspace.WorkspaceEntry;
import com.jnf.harness.domain.runtime.workspace.WorkspaceService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作区管理 REST（P9-1）：注册表 CRUD + 会话工作区解析查询。
 * <p>default 工作区不可删；rootPath 缺省时目录自动创建。
 */
@RestController
@RequestMapping("/api/harness/workspaces")
public class HarnessWorkspaceController {

    private final WorkspaceService service;

    public HarnessWorkspaceController(WorkspaceService service) {
        this.service = service;
    }

    @GetMapping
    public Response<List<Map<String, Object>>> list() {
        return Response.success(service.list().stream().map(HarnessWorkspaceController::json).toList());
    }

    @PostMapping
    public Response<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        try {
            WorkspaceEntry entry = service.create(str(body, "displayName"), str(body, "rootPath"));
            return Response.success(json(entry));
        } catch (IllegalArgumentException e) {
            return Response.invalidArgument(e.getMessage());
        }
    }

    @PutMapping("/{workspaceCode}")
    public Response<Map<String, Object>> update(@PathVariable String workspaceCode,
                                                @RequestBody Map<String, Object> body) {
        try {
            WorkspaceEntry entry = service.update(workspaceCode, str(body, "displayName"), str(body, "rootPath"));
            return Response.success(json(entry));
        } catch (IllegalArgumentException e) {
            return Response.invalidArgument(e.getMessage());
        }
    }

    @DeleteMapping("/{workspaceCode}")
    public Response<Map<String, Object>> delete(@PathVariable String workspaceCode) {
        try {
            int unbound = service.delete(workspaceCode);
            return Response.success(Map.of("workspaceCode", workspaceCode, "unboundSessions", unbound));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Response.invalidArgument(e.getMessage());
        }
    }

    /**
     * 目录浏览（P10-5：新建/编辑工作区时挑根目录，免手打绝对路径）。
     * <p>只列子目录；路径为空从用户主目录起；不可读路径不报错，在 error 字段里说明。
     */
    @GetMapping("/browse")
    public Response<Map<String, Object>> browse(
            @org.springframework.web.bind.annotation.RequestParam(value = "path", required = false) String path) {
        WorkspaceService.DirectoryListing listing = service.browse(path);
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("path", listing.path());
        json.put("parent", listing.parent());
        json.put("roots", listing.roots());
        json.put("directories", listing.directories().stream().map(child -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", child.name());
            item.put("path", child.path());
            return item;
        }).toList());
        json.put("error", listing.error());
        return Response.success(json);
    }

    /** 会话的工作区根（工具 cwd、沙箱工作区根的对外可见形态）。 */
    @GetMapping("/resolve")
    public Response<Map<String, Object>> resolve(
            @org.springframework.web.bind.annotation.RequestParam("sessionId") String sessionId) {
        String cwd = service.cwdFor(sessionId);
        return Response.success(Map.of("sessionId", sessionId, "cwd", cwd));
    }

    private static String str(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static Map<String, Object> json(WorkspaceEntry entry) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("workspaceCode", entry.workspaceCode());
        map.put("displayName", entry.displayName());
        map.put("rootPath", entry.rootPath());
        map.put("builtin", WorkspaceEntry.DEFAULT_CODE.equals(entry.workspaceCode()));
        return map;
    }
}

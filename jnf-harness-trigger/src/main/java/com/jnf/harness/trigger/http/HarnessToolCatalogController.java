package com.jnf.harness.trigger.http;

import com.jnf.harness.api.response.Response;
import com.jnf.harness.domain.runtime.tool.IToolCatalogPort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 工具目录 REST（A1 画像白名单管理）：画像的 toolProfile 绑定哪些工具由这里配。
 */
@RestController
@RequestMapping("/api/harness/tool-catalog")
public class HarnessToolCatalogController {

    private final IToolCatalogPort catalog;

    public HarnessToolCatalogController(IToolCatalogPort catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public Response<List<IToolCatalogPort.ToolCatalogEntry>> list() {
        return Response.success(catalog.findAll());
    }

    /** 某工具画像启用的工具（白名单）。 */
    @GetMapping("/profiles/{profileCode}")
    public Response<List<IToolCatalogPort.ToolCatalogEntry>> profile(@PathVariable String profileCode) {
        return Response.success(catalog.findEnabled(profileCode));
    }

    /** 整体替换某画像的工具绑定：{"toolCodes":["fs_read","ask_user_question"]}。 */
    @PutMapping("/profiles/{profileCode}")
    public Response<List<IToolCatalogPort.ToolCatalogEntry>> replaceBindings(
            @PathVariable String profileCode, @RequestBody Map<String, Object> body) {
        Object raw = body.get("toolCodes");
        List<String> codes = raw instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        catalog.replaceBindings(profileCode, codes);
        return Response.success(catalog.findEnabled(profileCode));
    }
}

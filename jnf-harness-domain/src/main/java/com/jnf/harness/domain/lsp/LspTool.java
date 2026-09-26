package com.jnf.harness.domain.lsp;

import com.jnf.harness.model.AbstractTool;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * lsp 工具（stub）：语言服务查询入口预留（LSP_NOT_CONFIGURED 结构化失败）。
 * <p>按计划"stub 类能力只留接口不做实现"（lsp/coderuntime/e2b 同策略）。
 */
public class LspTool extends AbstractTool {

    @Override
    public String name() {
        return "lsp_query";
    }

    @Override
    public String description() {
        return "语言服务查询（定义/引用）。当前未配置 LSP 时返回结构化提示。";
    }

    @Override
    public Map<String, Object> parameters() {
        return objectSchema()
                .prop("kind", stringSchema("definition/references"))
                .prop("file", stringSchema("文件路径"))
                .prop("line", stringSchema("行号"))
                .required("kind", "file", "line")
                .build();
    }

    @Override
    protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
        return fail("LSP 服务未配置（LSP_NOT_CONFIGURED）", "LSP_NOT_CONFIGURED");
    }
}

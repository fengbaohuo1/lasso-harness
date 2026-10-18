package com.jnf.harness.domain.extension;

import com.jnf.harness.domain.skill.SkillAdminService;
import com.jnf.harness.domain.skill.SkillInstallResult;
import com.jnf.harness.domain.skill.SkillSummary;
import com.jnf.harness.model.AbstractTool;
import com.jnf.harness.model.ToolDefinition;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * extension_* 对话工具族：extension_skills / extension_mcp / extension_cli（对话中管理扩展）。
 * <p>技能部分走真实 SKILL.md 运行时（{@link SkillAdminService}），不再使用内存登记桩。
 */
public final class ExtensionTools {

    private ExtensionTools() {
    }

    public static List<ToolDefinition> all(ExtensionManagementService service,
                                          SkillAdminService skillAdminService) {
        return List.of(new SkillsTool(skillAdminService), new McpTool(service), new CliTool(service));
    }

    static class SkillsTool extends AbstractTool {
        private final SkillAdminService skills;

        SkillsTool(SkillAdminService skills) {
            this.skills = skills;
        }

        @Override
        public String name() {
            return "extension_skills";
        }

        @Override
        public String description() {
            return "管理技能包（SKILL.md）：action=list/install/enable/disable/remove。"
                    + "list 列出当前工作区可见技能；install 需 source（Git 仓库 URL，可选 name），"
                    + "从仓库根或 subdir 定位 SKILL.md 并安装到用户技能目录；"
                    + "enable/disable/remove 需 name（remove 只对用户安装的技能生效）。";
        }

        @Override
        public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("action", stringSchema("list/install/enable/disable/remove"))
                    .prop("name", stringSchema("技能名（kebab-case；install 可省略，默认取 SKILL.md/目录名）"))
                    .prop("source", stringSchema("Git 仓库 URL（install 必填，https:// 或 git@ 或 ssh://）"))
                    .required("action").build();
        }

        @Override
        protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            String action = str(args, "action");
            String name = str(args, "name");
            try {
                String header;
                switch (action) {
                    case "install" -> {
                        SkillInstallResult result = skills.installFromGit(str(args, "source"), null, name);
                        if (!result.success()) {
                            return fail(result.message(), "SKILL_INSTALL_FAILED");
                        }
                        header = "技能已安装: " + result.name() + " → " + result.path() + "\n";
                    }
                    case "enable" -> {
                        skills.setEnabled(name, ctx.cwd(), true);
                        header = "已启用: " + name + "\n";
                    }
                    case "disable" -> {
                        skills.setEnabled(name, ctx.cwd(), false);
                        header = "已禁用: " + name + "\n";
                    }
                    case "remove" -> {
                        skills.remove(name);
                        header = "已删除: " + name + "\n";
                    }
                    case "list" -> header = "";
                    default -> {
                        return fail("未知 action: " + action, "SKILL_INVALID_ARGUMENT");
                    }
                }
                return ok(header + renderList(skills.list(ctx.cwd())));
            } catch (Exception e) {
                return fail(e.getMessage(), "SKILL_ERROR");
            }
        }

        private String renderList(List<SkillSummary> summaries) {
            if (summaries.isEmpty()) {
                return "（当前工作区没有可见技能）";
            }
            StringBuilder sb = new StringBuilder();
            for (SkillSummary skill : summaries) {
                sb.append(skill.name()).append(" | ")
                        .append(skill.enabled() ? "enabled" : "disabled").append(" | ")
                        .append(skill.source()).append(" | ")
                        .append(skill.description()).append('\n');
            }
            return sb.toString();
        }
    }

    static class McpTool extends AbstractTool {
        private final ExtensionManagementService service;

        McpTool(ExtensionManagementService service) {
            this.service = service;
        }

        @Override
        public String name() {
            return "extension_mcp";
        }

        @Override
        public String description() {
            return "管理 MCP server：action=list/add/remove/test（add 需 name + command）。";
        }

        @Override
        public Map<String, Object> parameters() {
            return objectSchema().prop("action", stringSchema("list/add/remove/test"))
                    .prop("name", stringSchema("server 名"))
                    .prop("command", stringSchema("启动命令（逗号分隔，add 用）"))
                    .required("action").build();
        }

        @Override
        protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            String action = str(args, "action");
            String name = str(args, "name");
            try {
                switch (action) {
                    case "add" -> service.addMcpServer(name, List.of(str(args, "command").split(",")));
                    case "remove" -> service.removeMcpServer(name);
                    case "test" -> {
                        int tools = service.testMcpServer(name);
                        return ok("连接成功，工具数=" + tools);
                    }
                    default -> {
                    }
                }
                StringBuilder sb = new StringBuilder();
                service.listMcpServers().forEach(s -> sb.append(s.name()).append(" | ")
                        .append(String.join(" ", s.command())).append(" | ")
                        .append(s.tested() ? "tested" : "untested").append('\n'));
                return ok(sb.length() == 0 ? "（无 MCP server）" : sb.toString());
            } catch (Exception e) {
                return fail(e.getMessage(), "EXTENSION_ERROR");
            }
        }
    }

    static class CliTool extends AbstractTool {
        private final ExtensionManagementService service;

        CliTool(ExtensionManagementService service) {
            this.service = service;
        }

        @Override
        public String name() {
            return "extension_cli";
        }

        @Override
        public String description() {
            return "管理 CLI 配置：action=list/set（set 需 key + value）。";
        }

        @Override
        public Map<String, Object> parameters() {
            return objectSchema().prop("action", stringSchema("list/set"))
                    .prop("key", stringSchema("键")).prop("value", stringSchema("值"))
                    .required("action").build();
        }

        @Override
        protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            if ("set".equals(str(args, "action"))) {
                service.setCli(str(args, "key"), str(args, "value"));
            }
            StringBuilder sb = new StringBuilder();
            service.cliConfig().forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
            return ok(sb.length() == 0 ? "（无 CLI 配置）" : sb.toString());
        }
    }
}

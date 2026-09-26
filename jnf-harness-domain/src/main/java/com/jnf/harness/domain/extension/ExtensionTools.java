package com.jnf.harness.domain.extension;

import com.jnf.harness.model.AbstractTool;
import com.jnf.harness.model.ToolDefinition;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * extension_* 对话工具族：extension_skills / extension_mcp / extension_cli（对话中管理扩展）。
 */
public final class ExtensionTools {

    private ExtensionTools() {
    }

    public static List<ToolDefinition> all(ExtensionManagementService service) {
        return List.of(new SkillsTool(service), new McpTool(service), new CliTool(service));
    }

    static class SkillsTool extends AbstractTool {
        private final ExtensionManagementService service;

        SkillsTool(ExtensionManagementService service) {
            this.service = service;
        }

        @Override
        public String name() {
            return "extension_skills";
        }

        @Override
        public String description() {
            return "管理技能：action=list/install/enable/disable/remove（name 必填除 list）。";
        }

        @Override
        public Map<String, Object> parameters() {
            return objectSchema().prop("action", stringSchema("list/install/enable/disable/remove"))
                    .prop("name", stringSchema("技能名")).prop("source", stringSchema("来源（install 用）"))
                    .required("action").build();
        }

        @Override
        protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            String action = str(args, "action");
            String name = str(args, "name");
            try {
                switch (action) {
                    case "install" -> service.installSkill(name, str(args, "source"));
                    case "enable" -> service.setSkillEnabled(name, true);
                    case "disable" -> service.setSkillEnabled(name, false);
                    case "remove" -> service.removeSkill(name);
                    default -> {
                    }
                }
                StringBuilder sb = new StringBuilder();
                service.listSkills().forEach(s -> sb.append(s.name()).append(" | ")
                        .append(s.enabled() ? "enabled" : "disabled").append(" | ")
                        .append(s.source()).append('\n'));
                return ok(sb.length() == 0 ? "（无技能）" : sb.toString());
            } catch (Exception e) {
                return fail(e.getMessage(), "EXTENSION_ERROR");
            }
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

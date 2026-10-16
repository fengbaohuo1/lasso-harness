package com.jnf.harness.app.config;

import com.jnf.harness.api.IAgentStreamApi;
import com.jnf.harness.cases.agent.AgentUseCaseImpl;
import com.jnf.harness.domain.agent.service.SystemPromptAssembler;
import com.jnf.harness.domain.agent.service.ToolRunner;
import com.jnf.harness.domain.agent.service.ask.AskUserTool;
import com.jnf.harness.domain.agent.service.ask.UserQuestionGateway;
import com.jnf.harness.domain.agent.service.compaction.BasicCompactionEngine;
import com.jnf.harness.domain.agent.service.compaction.CompactionEngine;
import com.jnf.harness.domain.agent.model.AgentOptions;
import com.jnf.harness.domain.agent.service.run.AgentRunFactory;
import com.jnf.harness.domain.llm.adapter.port.ILlmRuntimePort;
import com.jnf.harness.domain.llm.model.valobj.LlmChannel;
import com.jnf.harness.domain.runtime.attachment.IAttachmentStore;
import com.jnf.harness.domain.session.event.ISessionEventStore;
import com.jnf.harness.domain.tool.service.ToolCallExecutor;
import com.jnf.harness.domain.tool.service.DefaultToolRunContext;
import com.jnf.harness.domain.tool.service.InMemoryToolRegistry;
import com.jnf.harness.domain.tool.service.ToolRegistry;
import com.jnf.harness.domain.tool.shell.ShellCommandGuard;
import com.jnf.harness.domain.tool.shell.ShellCommandPolicy;
import com.jnf.harness.domain.tool.shell.IShellExecutor;
import com.jnf.harness.domain.tool.web.IWebPort;
import com.jnf.harness.infrastructure.adapter.eventstore.JsonlSessionEventStore;
import com.jnf.harness.infrastructure.adapter.llm.AnthropicAdapter;
import com.jnf.harness.infrastructure.adapter.llm.LlmRuntimeGateway;
import com.jnf.harness.infrastructure.adapter.llm.OllamaAdapter;
import com.jnf.harness.infrastructure.adapter.llm.OpenAiCompatibleAdapter;
import com.jnf.harness.infrastructure.adapter.shell.LocalShellExecutor;
import com.jnf.harness.infrastructure.adapter.web.HttpWebPort;
import com.jnf.harness.trigger.service.stream.AgentStreamApi;
import com.jnf.harness.model.ToolDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Harness 装配（组合根）：端口实现、工具注册、Agent 工厂、用例与流式门面。
 */
@Configuration
public class HarnessConfiguration {

    @Bean
    public ILlmRuntimePort llmRuntimePort(IAttachmentStore attachmentStore) {
        return new LlmRuntimeGateway(List.of(
                new OpenAiCompatibleAdapter(attachmentStore),
                new AnthropicAdapter(attachmentStore),
                new OllamaAdapter(attachmentStore)), 3);
    }

    @Bean
    public IShellExecutor shellExecutor() {
        return new LocalShellExecutor();
    }

    @Bean
    public IWebPort webPort() {
        return new HttpWebPort();
    }

    @Bean
    public com.jnf.harness.domain.agent.service.ask.UserQuestionGateway.InMemoryUserQuestionGateway userQuestionGateway() {
        // 具体类型暴露：RuntimeQuestionController 需要 pendingQuestions/answer（M8 问答闭环）
        return new com.jnf.harness.domain.agent.service.ask.UserQuestionGateway.InMemoryUserQuestionGateway();
    }


    /** R5/A1：按画像工具码现算工具 schema（可测缝：画像白名单裁 token 开销）。 */
    public static List<Map<String, Object>> buildSchemas(
            com.jnf.harness.domain.runtime.tool.IToolCatalogPort toolCatalogPort,
            ToolRegistry toolRegistry, String toolProfile) {
        java.util.Set<String> enabled = toolCatalogPort.findEnabled(
                toolProfile == null || toolProfile.isBlank()
                        ? com.jnf.harness.domain.agent.model.AgentOptions.DEFAULT_TOOL_PROFILE : toolProfile)
                .stream()
                .map(com.jnf.harness.domain.runtime.tool.IToolCatalogPort.ToolCatalogEntry::toolCode)
                .collect(java.util.stream.Collectors.toSet());
        List<Map<String, Object>> schemas = new ArrayList<>();
        for (ToolDefinition tool : toolRegistry.list()) {
            if (!enabled.isEmpty() && !enabled.contains(tool.name())) {
                continue;
            }
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("name", tool.name());
            schema.put("description", tool.description());
            schema.put("parameters", tool.parameters());
            schemas.add(schema);
        }
        return schemas;
    }
    /** A2 能力开关：harness.capabilities.<x>，默认全开；关闭=工具不注册（服务/REST 同步条件化）。 */
    static boolean capability(org.springframework.core.env.Environment env, String name) {
        return env.getProperty("harness.capabilities." + name, Boolean.class, true);
    }

    @Bean
    public ToolRegistry toolRegistry(UserQuestionGateway userQuestionGateway,
                                     com.jnf.harness.domain.goal.GoalService goalService,
                                     com.jnf.harness.domain.jobs.JobRegistryService jobService,
                                     com.jnf.harness.domain.schedule.ScheduleService scheduleService,
                                     com.jnf.harness.domain.terminal.TerminalService terminalService,
                                     com.jnf.harness.domain.extension.ExtensionManagementService extensionService,
                                     org.springframework.core.env.Environment env) {
        // P2/A3：内核零内置工具——fs/shell/web 族官方插件 JAR；essentials 族（todo/plan/lsp/session_search）
        // 亦为插件；这里只留依赖领域服务的内核工具（A2 开关裁注册）
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(new AskUserTool(userQuestionGateway, 300));
        // 第 12 节：jobs / goal / schedule / plan 工具族
        if (capability(env, "jobs")) {
            com.jnf.harness.domain.jobs.JobTools.all(jobService).forEach(registry::register);
        }
        if (capability(env, "goal")) {
            registry.register(new com.jnf.harness.domain.goal.GoalTool(goalService));
        }
        if (capability(env, "schedule")) {
            com.jnf.harness.domain.schedule.ScheduleTools.all(scheduleService).forEach(registry::register);
        }
        // 第 12b：terminal / extension / session_search / lsp 工具族
        if (capability(env, "terminal")) {
            com.jnf.harness.domain.terminal.TerminalTools.all(terminalService).forEach(registry::register);
        }
        if (capability(env, "extension")) {
            com.jnf.harness.domain.extension.ExtensionTools.all(extensionService).forEach(registry::register);
        }
        return registry;
    }

    /** 宿主服务（P1）：受治理 shell 执行通道（R2 命令守卫内建），供 shell 工具插件查询。 */
    @Bean
    public com.jnf.harness.spi.host.ShellService shellHostService(
            IShellExecutor shellExecutor,
            com.jnf.harness.domain.tool.shell.ShellCommandPolicy shellCommandPolicy) {
        return new com.jnf.harness.domain.tool.shell.ShellHostService(shellExecutor,
                new com.jnf.harness.domain.tool.shell.ShellCommandGuard(shellCommandPolicy),
                Path.of("").toAbsolutePath());
    }

    @Bean
    public com.jnf.harness.domain.tool.shell.ShellCommandPolicy shellCommandPolicy() {
        return com.jnf.harness.domain.tool.shell.ShellCommandPolicy.defaultPolicy();
    }

    /** 宿主服务（A3）：跨会话历史检索，供 session_search 工具插件查询。 */
    @Bean
    public com.jnf.harness.spi.host.SessionSearchService sessionSearchHostService(ISessionEventStore eventStore) {
        return new com.jnf.harness.domain.session.SessionSearchHostService(eventStore);
    }

    /** 宿主服务（P1）：Web 出网通道，供 web 工具插件查询。 */
    @Bean
    public com.jnf.harness.spi.host.WebService webHostService(IWebPort webPort) {
        return new com.jnf.harness.domain.tool.web.WebHostService(webPort);
    }

    // ---------------- 第 12b 节：workflow / terminal / extension ----------------

    @Bean
    public com.jnf.harness.domain.workflow.WorkflowService workflowService(AgentRunFactory agentRunFactory) {
        return new com.jnf.harness.domain.workflow.WorkflowService(agentRunFactory);
    }

    @Bean
    public com.jnf.harness.domain.terminal.TerminalService terminalService() {
        return new com.jnf.harness.domain.terminal.TerminalService();
    }

    @Bean
    public com.jnf.harness.domain.extension.ExtensionManagementService extensionManagementService() {
        return new com.jnf.harness.domain.extension.ExtensionManagementService(
                command -> new com.jnf.harness.infrastructure.adapter.plugin.JsonRpcStdioBridge(
                        Path.of(".").toAbsolutePath(), command));
    }

    // ---------------- 第 13 节：开放协议与数字人 ----------------

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "harness.capabilities.a2a", havingValue = "true", matchIfMissing = true)
@Bean
    public com.jnf.harness.domain.acp.A2aTaskService a2aTaskService() {
        return new com.jnf.harness.domain.acp.A2aTaskService();
    }

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "harness.capabilities.digital-human", havingValue = "true", matchIfMissing = true)
@Bean
    public com.jnf.harness.domain.digitalhuman.DigitalHumanService digitalHumanService(
            org.springframework.jdbc.core.JdbcTemplate jdbc) {
        return new com.jnf.harness.domain.digitalhuman.DigitalHumanService(
                new com.jnf.harness.infrastructure.adapter.setting.DigitalHumanRepository(jdbc));
    }

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "harness.capabilities.digital-human", havingValue = "true", matchIfMissing = true)
@Bean
    public com.jnf.harness.infrastructure.adapter.web.RemoteAgentDiscoveryService remoteAgentDiscoveryService() {
        return new com.jnf.harness.infrastructure.adapter.web.RemoteAgentDiscoveryService();
    }

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "harness.capabilities.collaboration", havingValue = "true", matchIfMissing = true)
@Bean
    public com.jnf.harness.domain.collaboration.CollaborationService collaborationService(
            org.springframework.jdbc.core.JdbcTemplate jdbc) {
        return new com.jnf.harness.domain.collaboration.CollaborationService(
                new com.jnf.harness.infrastructure.adapter.setting.CollaborationRepository(jdbc));
    }

    // ---------------- 第 12 节：goal / jobs / schedule / plan（R7 持久化） ----------------

    @Bean
    public com.jnf.harness.domain.goal.GoalService goalService(org.springframework.jdbc.core.JdbcTemplate jdbc) {
        return new com.jnf.harness.domain.goal.GoalService(
                new com.jnf.harness.infrastructure.adapter.setting.RuntimeStateRepositories.GoalRepository(jdbc));
    }

    @Bean
    public com.jnf.harness.domain.jobs.JobRegistryService jobRegistryService(IShellExecutor shellExecutor,
                                                                             org.springframework.jdbc.core.JdbcTemplate jdbc) {
        return new com.jnf.harness.domain.jobs.JobRegistryService(
                new com.jnf.harness.infrastructure.adapter.setting.RuntimeStateRepositories.JobRepository(jdbc),
                shellExecutor);
    }

    @Bean
    public com.jnf.harness.domain.schedule.ScheduleService scheduleService(
            org.springframework.jdbc.core.JdbcTemplate jdbc) {
        return new com.jnf.harness.domain.schedule.ScheduleService(
                new com.jnf.harness.infrastructure.adapter.setting.RuntimeStateRepositories.ScheduleRepository(jdbc));
    }


    @Bean
    public com.jnf.harness.domain.tool.service.ToolHookRegistry toolHookRegistry() {
        return new com.jnf.harness.domain.tool.service.ToolHookRegistry();
    }

    // ---------------- 第 10 节：运行期审批（R1/R10） ----------------

    @Bean
    public com.jnf.harness.domain.task.approval.IApprovalRecordRepository approvalRecordRepository(
            com.jnf.harness.infrastructure.dao.ApprovalRecordDao dao) {
        return new com.jnf.harness.infrastructure.adapter.setting.ApprovalRecordRepository(dao);
    }

    @Bean
    public com.jnf.harness.domain.task.approval.RuntimeApprovalService runtimeApprovalService(
            com.jnf.harness.domain.task.approval.IApprovalRecordRepository repository) {
        return new com.jnf.harness.domain.task.approval.RuntimeApprovalService(repository);
    }

    @Bean
    public com.jnf.harness.domain.task.approval.RuntimeApprovalGate runtimeApprovalGate(
            com.jnf.harness.domain.task.approval.RuntimeApprovalService service,
            com.jnf.harness.domain.runtime.tool.IToolCatalogPort toolCatalogPort,
            @Value("${harness.approval.mode:REQUEST_APPROVAL}") String mode,
            @Value("${harness.approval.timeout-seconds:300}") long timeoutSeconds) {
        // R1：默认 REQUEST_APPROVAL——目录中标记 requiresApproval 的工具挂起待裁决
        java.util.Set<String> risky = new java.util.HashSet<>();
        toolCatalogPort.findAll().forEach(entry -> {
            if (entry.requiresApproval()) {
                risky.add(entry.toolCode());
            }
        });
        com.jnf.harness.domain.task.approval.RuntimeApprovalGate.Mode gateMode =
                "AUTO_APPROVE".equalsIgnoreCase(mode)
                        ? com.jnf.harness.domain.task.approval.RuntimeApprovalGate.Mode.AUTO_APPROVE
                        : com.jnf.harness.domain.task.approval.RuntimeApprovalGate.Mode.REQUEST_APPROVAL;
        return new com.jnf.harness.domain.task.approval.RuntimeApprovalGate(
                service, gateMode, risky::contains, "session", timeoutSeconds);
    }

    // P9-2：静态 SandboxGuardHook 退役——三档权限由沙箱插件（jnf-harness-plugin-sandbox）
    // 经 PRE_TOOL_USE Hook 运行期强制（按会话设模式），内核只留插件 Hook 落点。

    @Bean
    public ToolRunner toolRunner(ToolRegistry toolRegistry,
                                 com.jnf.harness.domain.tool.service.ToolHookRegistry hookRegistry,
                                 com.jnf.harness.domain.task.approval.RuntimeApprovalGate approvalGate) {
        return new ToolCallExecutor(toolRegistry, List.of(hookRegistry), approvalGate,
                new DefaultToolRunContext("agent", "session",
                        Path.of("").toAbsolutePath().toString()), 300, 10);
    }

    // ---------------- P9-1：工作区管理 ----------------

    @Bean
    public com.jnf.harness.domain.runtime.workspace.IWorkspaceRepository workspaceRepository(
            com.jnf.harness.infrastructure.dao.WorkspaceDao dao) {
        return new com.jnf.harness.infrastructure.adapter.setting.WorkspaceRepository(dao);
    }

    @Bean
    public com.jnf.harness.domain.runtime.workspace.WorkspaceService workspaceService(
            com.jnf.harness.domain.runtime.workspace.IWorkspaceRepository repository,
            com.jnf.harness.domain.session.meta.SessionMetaService sessionMetaService) {
        return new com.jnf.harness.domain.runtime.workspace.WorkspaceService(repository, sessionMetaService);
    }

    // ---------------- 第 10 节收尾：任务五子域 ----------------

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "harness.capabilities.task", havingValue = "true", matchIfMissing = true)
@Bean
    public com.jnf.harness.domain.task.IHarnessTaskQueueRepository harnessTaskQueueRepository(
            com.jnf.harness.infrastructure.dao.HarnessTaskDao dao) {
        return new com.jnf.harness.infrastructure.adapter.setting.HarnessTaskRepository(dao);
    }

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "harness.capabilities.task", havingValue = "true", matchIfMissing = true)
@Bean
    public com.jnf.harness.domain.task.HarnessExecutionService harnessExecutionService(
            com.jnf.harness.domain.task.IHarnessTaskQueueRepository queue,
            AgentRunFactory agentRunFactory) {
        return new com.jnf.harness.domain.task.HarnessExecutionService(queue, agentRunFactory);
    }

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "harness.capabilities.task", havingValue = "true", matchIfMissing = true)
@Bean
    public com.jnf.harness.domain.task.TaskSubmitService taskSubmitService(
            com.jnf.harness.domain.runtime.tool.IToolCatalogPort toolCatalogPort,
            com.jnf.harness.domain.task.IHarnessTaskQueueRepository queue,
            com.jnf.harness.domain.task.HarnessExecutionService execution) {
        return new com.jnf.harness.domain.task.TaskSubmitService(
                new com.jnf.harness.domain.task.TaskSubmissionPolicyService(toolCatalogPort),
                new com.jnf.harness.domain.task.PermissionPolicyService(toolCatalogPort),
                new com.jnf.harness.domain.task.ApprovalPolicyService(),
                queue, execution);
    }

    // ---------------- 第 9 节：插件体系 ----------------

    @Bean
    public com.jnf.harness.spi.PluginEventBus pluginEventBus() {
        return new com.jnf.harness.domain.plugin.InMemoryPluginEventBus();
    }

    @Bean
    public com.jnf.harness.domain.plugin.registry.IPluginConfigRepository pluginConfigRepository(
            com.jnf.harness.infrastructure.dao.PluginConfigDao dao) {
        return new com.jnf.harness.infrastructure.adapter.plugin.PluginConfigRepository(dao);
    }

    @Bean
    public com.jnf.harness.spi.PluginConfigStore pluginConfigStore(
            com.jnf.harness.domain.plugin.registry.IPluginConfigRepository repository) {
        // P1：DB 持久化替换内存实现（重启配置不丢）
        return new com.jnf.harness.infrastructure.adapter.plugin.JdbcPluginConfigStore(repository);
    }

    @Bean
    public com.jnf.harness.domain.plugin.registry.PluginConfigService pluginConfigService(
            com.jnf.harness.domain.plugin.registry.IPluginConfigRepository repository) {
        return new com.jnf.harness.domain.plugin.registry.PluginConfigService(repository);
    }

    @Bean
    public com.jnf.harness.domain.plugin.registry.IPluginCatalogPort pluginCatalogPort() {
        return new com.jnf.harness.infrastructure.adapter.plugin.PluginCatalogService();
    }

    @Bean
    public com.jnf.harness.domain.plugin.IPluginLoaderPort pluginLoader() {
        return new com.jnf.harness.infrastructure.adapter.plugin.JavaPluginLoader();
    }

    @Bean
    public com.jnf.harness.domain.plugin.PluginEndpointRegistry pluginEndpointRegistry() {
        // SD3：插件 REST 端点注册表（随插件事务边界回收）
        return new com.jnf.harness.domain.plugin.PluginEndpointRegistry();
    }

    @Bean
    public com.jnf.harness.domain.plugin.PluginHost pluginHost(
            com.jnf.harness.domain.plugin.IPluginLoaderPort loader,
            ToolRegistry toolRegistry, SystemPromptAssembler promptAssembler,
            com.jnf.harness.domain.tool.service.ToolHookRegistry hookRegistry,
            com.jnf.harness.spi.PluginEventBus eventBus,
            com.jnf.harness.spi.PluginConfigStore configStore,
            com.jnf.harness.domain.runtime.tool.IToolCatalogPort toolCatalog,
            com.jnf.harness.domain.plugin.PluginEndpointRegistry endpointRegistry,
            com.jnf.harness.spi.host.ShellService shellHostService,
            com.jnf.harness.spi.host.WebService webHostService,
            com.jnf.harness.spi.host.SessionSearchService sessionSearchHostService,
            com.jnf.harness.spi.host.AgentRunService agentRunHostService) {
        // P1 宿主服务查询：插件经 lookupService 取得受治理的执行通道
        Map<Class<?>, Object> hostServices = Map.of(
                com.jnf.harness.spi.host.ShellService.class, shellHostService,
                com.jnf.harness.spi.host.WebService.class, webHostService,
                com.jnf.harness.spi.host.SessionSearchService.class, sessionSearchHostService,
                com.jnf.harness.spi.host.AgentRunService.class, agentRunHostService);
        return new com.jnf.harness.domain.plugin.PluginHost(loader, toolRegistry,
                promptAssembler, hookRegistry, eventBus, configStore, hostServices, toolCatalog,
                endpointRegistry);
    }

    /** P8-3：子智能体宿主服务（深度 ≤2 防递归 + 超时收口），供子智能体插件 lookupService 取用。 */
    @Bean
    public com.jnf.harness.domain.agent.service.run.SubagentHostService agentRunHostService(
            com.jnf.harness.domain.agent.service.run.AgentRunFactory agentRunFactory) {
        return new com.jnf.harness.domain.agent.service.run.SubagentHostService(agentRunFactory);
    }

    @Bean
    public com.jnf.harness.domain.plugin.registry.IPluginInstallationRepository pluginInstallationRepository(
            com.jnf.harness.infrastructure.dao.PluginInstallationDao dao) {
        return new com.jnf.harness.infrastructure.adapter.plugin.PluginInstallationRepository(dao);
    }

    @Bean
    public com.jnf.harness.domain.tool.mcp.McpServerService mcpServerService(ToolRegistry toolRegistry) {
        return new com.jnf.harness.domain.tool.mcp.McpServerService(toolRegistry);
    }

    @Bean
    public com.jnf.harness.domain.plugin.INodePluginLoaderPort nodePluginLoader() {
        return new com.jnf.harness.infrastructure.adapter.plugin.NodePluginLoader();
    }

    @Bean
    public com.jnf.harness.domain.plugin.registry.IPluginRuntimeBindingRepository pluginRuntimeBindingRepository(
            com.jnf.harness.infrastructure.dao.PluginRuntimeBindingDao dao) {
        return new com.jnf.harness.infrastructure.adapter.plugin.PluginRuntimeBindingRepository(dao);
    }

    @Bean
    public com.jnf.harness.domain.plugin.registry.PluginRegistryService pluginRegistryService(
            com.jnf.harness.domain.plugin.registry.IPluginInstallationRepository repository,
            com.jnf.harness.domain.plugin.PluginHost pluginHost,
            com.jnf.harness.domain.plugin.INodePluginLoaderPort nodePluginLoader,
            com.jnf.harness.domain.plugin.registry.IPluginRuntimeBindingRepository runtimeBinding,
            @Value("${harness.plugins.dir:}") String configuredPluginsDir) {
        // P3：sourcePath 相对化按当前插件目录重解析（跨机器可迁移）
        return new com.jnf.harness.domain.plugin.registry.PluginRegistryService(
                repository, pluginHost, nodePluginLoader, runtimeBinding,
                com.jnf.harness.domain.plugin.registry.PluginDirectoryResolver.resolve(configuredPluginsDir));
    }

    @Bean
    public com.jnf.harness.app.boot.PluginBootActivator pluginBootActivator(
            com.jnf.harness.domain.plugin.registry.PluginRegistryService registry,
            com.jnf.harness.domain.plugin.PluginHost pluginHost,
            com.jnf.harness.domain.plugin.registry.IPluginCatalogPort catalog,
            @Value("${harness.plugins.dir:}") String configuredPluginsDir,
            @Value("${harness.plugins.auto-load:true}") boolean autoLoad) {
        // P3：目录自动装载 + DB 恢复 + classpath 发现
        return new com.jnf.harness.app.boot.PluginBootActivator(
                registry, pluginHost, catalog, configuredPluginsDir, autoLoad);
    }

    @Bean
    public SystemPromptAssembler systemPromptAssembler() {
        SystemPromptAssembler assembler = new SystemPromptAssembler();
        assembler.registerSection(new SystemPromptAssembler.PromptSection("base", 0,
                "你是 jnf-harness 智能体，通过工具调用完成用户的任务。"));
        return assembler;
    }

    @Bean
    public CompactionEngine compactionEngine() {
        // 简版摘要器（LLM 摘要器随第 8 节渠道化升级）
        return new BasicCompactionEngine(events -> "[摘要] 此前 " + events.size() + " 条消息事件已压缩。");
    }

    /** 会话事件/元数据根目录（F3：meta.json 与 events.jsonl 同目录）。 */
    public static final String EVENTS_DIR_PROPERTY = "harness.events.dir";

    @Bean
    public ISessionEventStore sessionEventStore(
            @Value("${harness.events.dir:./data/events}") String eventsRoot) {
        return new JsonlSessionEventStore(Path.of(eventsRoot));
    }

    @Bean
    public com.jnf.harness.domain.session.meta.ISessionMetaStore sessionMetaStore(
            @Value("${harness.events.dir:./data/events}") String eventsRoot) {
        return new com.jnf.harness.infrastructure.adapter.session.FileSessionMetaStore(Path.of(eventsRoot));
    }

    @Bean
    public com.jnf.harness.domain.session.meta.SessionMetaService sessionMetaService(
            com.jnf.harness.domain.session.meta.ISessionMetaStore store) {
        return new com.jnf.harness.domain.session.meta.SessionMetaService(store);
    }

    // ---------------- 第 8 节：持久化与模型渠道 ----------------

    @Bean
    public com.jnf.harness.domain.runtime.setting.IModelSettingRepository modelSettingRepository(
            com.jnf.harness.infrastructure.dao.ModelSettingDao dao) {
        return new com.jnf.harness.infrastructure.adapter.setting.ModelSettingRepository(dao);
    }

    @Bean
    public com.jnf.harness.domain.runtime.secret.ISecretStore secretStore() {
        // M1：本地密钥库（data/secrets.json，Base64 混淆；R9 边界见计划 SD1）
        return new com.jnf.harness.infrastructure.adapter.secret.FileSecretStore(Path.of("./data/secrets.json"));
    }

    @Bean
    public IAttachmentStore attachmentStore(
            @Value("${harness.attachments.dir:./data/attachments}") String dir) {
        // P11-4：图片附件落盘（事件里只留引用，JSONL 不被 base64 撑爆）
        return new com.jnf.harness.infrastructure.adapter.attachment.FileAttachmentStore(Path.of(dir));
    }

    @Bean
    public com.jnf.harness.domain.runtime.secret.SecretRefResolver secretRefResolver(
            com.jnf.harness.domain.runtime.secret.ISecretStore store) {
        return new com.jnf.harness.domain.runtime.secret.SecretRefResolver(store);
    }

    @Bean
    public com.jnf.harness.domain.runtime.setting.ModelSettingService modelSettingService(
            com.jnf.harness.domain.runtime.setting.IModelSettingRepository repository,
            com.jnf.harness.domain.runtime.secret.SecretRefResolver secrets) {
        return new com.jnf.harness.domain.runtime.setting.ModelSettingService(repository, secrets);
    }

    @Bean
    public com.jnf.harness.domain.runtime.setting.IModelRepository modelRepository(
            com.jnf.harness.infrastructure.dao.ModelDao dao) {
        return new com.jnf.harness.infrastructure.adapter.setting.ModelRepository(dao);
    }

    @Bean
    public com.jnf.harness.domain.runtime.setting.ModelService modelService(
            com.jnf.harness.domain.runtime.setting.IModelRepository repository,
            com.jnf.harness.domain.runtime.setting.IModelSettingRepository channelRepository) {
        // P11-1：渠道下的模型清单（harness_model 子表），渠道表 model_code 为「默认模型」镜像
        return new com.jnf.harness.domain.runtime.setting.ModelService(repository, channelRepository);
    }

    @Bean
    public com.jnf.harness.app.boot.ModelSettingBootSeed modelSettingBootSeed(
            com.jnf.harness.domain.runtime.setting.ModelSettingService service,
            com.jnf.harness.domain.runtime.setting.ModelService models,
            @Value("${harness.llm.default.base-url:}") String baseUrl,
            @Value("${harness.llm.default.api-key:}") String apiKey,
            @Value("${harness.llm.default.model:}") String model,
            @Value("${harness.llm.seed-default-channel:true}") boolean seedEnabled) {
        // M2：首启 seed（渠道表空时把文件配置落成 default 渠道；测试可关）
        return new com.jnf.harness.app.boot.ModelSettingBootSeed(service, models, baseUrl, apiKey, model, seedEnabled);
    }

    @Bean
    public com.jnf.harness.domain.runtime.tool.IToolCatalogPort toolCatalogPort(
            com.jnf.harness.infrastructure.dao.ToolCatalogDao dao) {
        return new com.jnf.harness.infrastructure.adapter.setting.ToolCatalogRepository(dao);
    }

    @Bean
    public com.jnf.harness.domain.runtime.model.IModelDiscoveryPort modelDiscoveryPort() {
        return new com.jnf.harness.infrastructure.adapter.setting.ModelDiscoveryService();
    }

    @Bean
    public com.jnf.harness.domain.agent.profile.IAgentProfileRepository agentProfileRepository(
            com.jnf.harness.infrastructure.dao.AgentProfileDao dao) {
        return new com.jnf.harness.infrastructure.adapter.setting.AgentProfileRepository(dao);
    }

    @Bean
    public com.jnf.harness.domain.agent.profile.AgentProfileService agentProfileService(
            com.jnf.harness.domain.agent.profile.IAgentProfileRepository repository) {
        // A1：智能体画像（步数/预算/工具画像/提示词补丁按场景裁）
        return new com.jnf.harness.domain.agent.profile.AgentProfileService(repository);
    }

    @Bean
    public AgentRunFactory agentRunFactory(ILlmRuntimePort llmRuntimePort, ToolRunner toolRunner,
                                           ToolRegistry toolRegistry, ISessionEventStore store,
                                           SystemPromptAssembler promptAssembler,
                                           CompactionEngine compactionEngine,
                                           com.jnf.harness.domain.runtime.tool.IToolCatalogPort toolCatalogPort,
                                           com.jnf.harness.domain.runtime.setting.ModelSettingService modelSettingService,
                                           com.jnf.harness.domain.runtime.setting.ModelService modelService,
                                           com.jnf.harness.domain.agent.profile.AgentProfileService profileService,
                                           com.jnf.harness.domain.runtime.workspace.WorkspaceService workspaceService,
                                           @Value("${harness.llm.default.base-url}") String baseUrl,
                                           @Value("${harness.llm.default.api-key}") String apiKey,
                                           @Value("${harness.llm.default.model}") String model) {
        // R5：工具目录真接线——只暴露画像启用的工具（toolProfile 来自画像，SD2）。
        // P3 修复：schema 每次现算（插件激活在 ApplicationRunner，晚于 Bean 构建；
        // 冻结快照会让插件工具永远不进模型工具表）
        java.util.function.Function<String, List<Map<String, Object>>> schemasSupplier =
                toolProfile -> buildSchemas(toolCatalogPort, toolRegistry, toolProfile);
        // F2/A1/P9-7/P9-8/P11-6：模型 + 预设 + 思考强度解析。
        // 模型提示（会话选择器的值）优先于预设，形态既可能是「渠道::模型」精确引用，也可能是裸模型码；
        // 命中 harness_model 则整渠道切换并按官方规格定上下文预算/输出上限，未命中回落生效渠道；
        // 思考强度提示优先于预设；预设旋钮/工具画像/提示词补丁按 profile 提示裁
        com.jnf.harness.domain.agent.model.AgentOptionsResolver optionsResolver =
                (hint, profileHint, thinkingHint) -> {
            com.jnf.harness.domain.agent.profile.AgentProfile profile = profileService.resolve(profileHint);
            String effectiveHint = (hint != null && !hint.isBlank()) ? hint : profile.modelCode();

            com.jnf.harness.domain.runtime.setting.ModelEntity spec =
                    modelService.resolve(effectiveHint).orElse(null);
            com.jnf.harness.domain.runtime.setting.ModelSettingEntity selected = null;
            if (spec != null) {
                selected = modelSettingService.list().stream()
                        .filter(item -> item.channelCode().equals(spec.channelCode()))
                        .findFirst()
                        .orElse(null);
            }
            if (selected == null) {
                selected = modelSettingService.active().orElse(null);
            }
            String modelCode;
            LlmChannel channel;
            if (selected != null) {
                modelCode = spec != null ? spec.modelCode()
                        : (effectiveHint != null && !effectiveHint.isBlank()
                                ? effectiveHint : selected.modelCode());
                channel = new LlmChannel(selected.baseUrl(),
                        modelSettingService.secrets().resolve(selected.apiKeyRef()),
                        com.jnf.harness.domain.llm.model.valobj.LlmProtocol.from(selected.protocol()));
            } else {
                modelCode = effectiveHint != null && !effectiveHint.isBlank() ? effectiveHint : model;
                channel = LlmChannel.openai(baseUrl, apiKey);
            }
            // P11-6：上下文预算取「预设策略上限」与「模型官方窗口」的较小值；
            // 输出上限同理，预设未配则直接用模型规格（适配器的协议兜底不再被硬编码值占位）
            long contextBudget = profile.contextTokenBudget();
            long compactionThreshold = profile.compactionThresholdTokens();
            Integer maxTokens = profile.maxTokens();
            if (spec != null) {
                long contextWindow = spec.effectiveContextWindow();
                contextBudget = Math.min(contextBudget, contextWindow);
                compactionThreshold = Math.min(compactionThreshold,
                        Math.max(1_000L, (long) (contextBudget * 0.8)));
                int ceiling = spec.effectiveMaxOutputTokens();
                maxTokens = maxTokens == null ? ceiling : Math.min(maxTokens, ceiling);
            }
            // P9-8：思考强度——per-message 提示优先于预设（空=不传，由供应商默认）
            String reasoningEffort = (thinkingHint != null && !thinkingHint.isBlank())
                    ? thinkingHint : profile.reasoningEffort();
            return new AgentOptions(modelCode, channel,
                    profile.maxStepsPerTurn(), profile.maxTokenContinuations(),
                    contextBudget, profile.keepRecentMessages(),
                    compactionThreshold, maxTokens, profile.temperature(),
                    profile.toolProfile(), profile.promptExtra(), reasoningEffort);
        };
        return new AgentRunFactory(llmRuntimePort, toolRunner, optionsResolver,
                schemasSupplier, store, promptAssembler, compactionEngine,
                workspaceService::cwdFor);
    }

    @Bean
    public AgentUseCaseImpl agentUseCase(AgentRunFactory agentRunFactory) {
        return new AgentUseCaseImpl(agentRunFactory);
    }

    @Bean
    public AgentStreamApi agentStreamApi(IAgentStreamApi streamApi) {
        return new AgentStreamApi(streamApi);
    }
}

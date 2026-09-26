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
import com.jnf.harness.domain.session.event.ISessionEventStore;
import com.jnf.harness.domain.tool.fs.FsGrepTool;
import com.jnf.harness.domain.tool.fs.FsReadImageTool;
import com.jnf.harness.domain.tool.fs.FsReadTool;
import com.jnf.harness.domain.tool.fs.FsSearchTool;
import com.jnf.harness.domain.tool.fs.FsWriteTool;
import com.jnf.harness.domain.tool.fs.StrReplaceEditorTool;
import com.jnf.harness.domain.tool.service.ToolCallExecutor;
import com.jnf.harness.domain.tool.service.DefaultToolRunContext;
import com.jnf.harness.domain.tool.service.InMemoryToolRegistry;
import com.jnf.harness.domain.tool.service.ToolRegistry;
import com.jnf.harness.domain.tool.shell.ShellCommandGuard;
import com.jnf.harness.domain.tool.shell.ShellCommandPolicy;
import com.jnf.harness.domain.tool.shell.ShellExecuteTool;
import com.jnf.harness.domain.tool.shell.IShellExecutor;
import com.jnf.harness.domain.tool.web.IWebPort;
import com.jnf.harness.domain.tool.web.WebFetchTool;
import com.jnf.harness.domain.tool.web.WebSearchTool;
import com.jnf.harness.domain.todo.TodoService;
import com.jnf.harness.domain.todo.TodoWriteTool;
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
    public ILlmRuntimePort llmRuntimePort() {
        return new LlmRuntimeGateway(List.of(
                new OpenAiCompatibleAdapter(), new AnthropicAdapter(), new OllamaAdapter()), 3);
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
    public UserQuestionGateway userQuestionGateway() {
        return new UserQuestionGateway.InMemoryUserQuestionGateway();
    }

    @Bean
    public TodoService todoService() {
        return new TodoService();
    }

    @Bean
    public ToolRegistry toolRegistry(IShellExecutor shellExecutor, IWebPort webPort,
                                     UserQuestionGateway userQuestionGateway, TodoService todoService) {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(new FsReadTool());
        registry.register(new FsWriteTool());
        registry.register(new FsSearchTool());
        registry.register(new FsGrepTool());
        registry.register(new FsReadImageTool());
        registry.register(new StrReplaceEditorTool());
        registry.register(new ShellExecuteTool(shellExecutor,
                new ShellCommandGuard(ShellCommandPolicy.defaultPolicy())));
        registry.register(new WebFetchTool(webPort));
        registry.register(new WebSearchTool(webPort));
        registry.register(new AskUserTool(userQuestionGateway, 300));
        registry.register(new TodoWriteTool(todoService));
        return registry;
    }

    @Bean
    public ToolRunner toolRunner(ToolRegistry toolRegistry) {
        return new ToolCallExecutor(toolRegistry, List.of(), null,
                new DefaultToolRunContext("agent", "session",
                        Path.of("").toAbsolutePath().toString()), 300, 10);
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

    @Bean
    public ISessionEventStore sessionEventStore() {
        return new JsonlSessionEventStore(Path.of("./data/events"));
    }

    @Bean
    public AgentRunFactory agentRunFactory(ILlmRuntimePort llmRuntimePort, ToolRunner toolRunner,
                                           ToolRegistry toolRegistry, ISessionEventStore store,
                                           SystemPromptAssembler promptAssembler,
                                           CompactionEngine compactionEngine,
                                           @Value("${harness.llm.default.base-url}") String baseUrl,
                                           @Value("${harness.llm.default.api-key}") String apiKey,
                                           @Value("${harness.llm.default.model}") String model) {
        List<Map<String, Object>> schemas = new ArrayList<>();
        for (ToolDefinition tool : toolRegistry.list()) {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("name", tool.name());
            schema.put("description", tool.description());
            schema.put("parameters", tool.parameters());
            schemas.add(schema);
        }
        AgentOptions options = AgentOptions.of(model, LlmChannel.openai(baseUrl, apiKey));
        return new AgentRunFactory(llmRuntimePort, toolRunner, () -> options,
                () -> schemas, store, promptAssembler, compactionEngine);
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

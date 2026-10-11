package com.jnf.harness.domain.tool;

import com.jnf.harness.domain.agent.service.ask.AskUserTool;
import com.jnf.harness.domain.agent.service.ask.UserQuestionGateway;
import com.jnf.harness.plugin.fs.FsGrepTool;
import com.jnf.harness.plugin.fs.FsReadImageTool;
import com.jnf.harness.plugin.fs.FsSearchTool;
import com.jnf.harness.domain.tool.web.IWebPort;
import com.jnf.harness.plugin.web.WebFetchTool;
import com.jnf.harness.plugin.web.WebSearchTool;
import com.jnf.harness.plugin.essentials.TodoService;
import com.jnf.harness.plugin.essentials.TodoWriteTool;
import com.jnf.harness.model.ToolExecutionResult;
import com.jnf.harness.model.ToolRunContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工具族补测（第 5 节收口）：search/grep/read_image/web/ask_user/todo。
 */
class MoreToolsTest {

    private final ToolRunContext ctx = new ToolRunContext() {
        @Override
        public String agentId() {
            return "agent-1";
        }

        @Override
        public String sessionId() {
            return "s-1";
        }

        @Override
        public String cwd() {
            return ".";
        }
    };

    @Test
    @DisplayName("fs_search glob 命中 + fs_grep 内容命中")
    void search_and_grep(@TempDir Path tempDir) throws Exception {
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src/A.java"), "class A { String name; }");
        Files.writeString(tempDir.resolve("src/B.txt"), "hello name");

        ToolRunContext tempCtx = new ToolRunContext() {
            @Override
            public String agentId() {
                return "agent-1";
            }

            @Override
            public String sessionId() {
                return "s-1";
            }

            @Override
            public String cwd() {
                return tempDir.toString();
            }
        };

        ToolExecutionResult search = new FsSearchTool()
                .execute(Map.of("pattern", "**/*.java"), tempCtx).get();
        assertTrue(search.content().contains("src/A.java"));

        ToolExecutionResult grep = new FsGrepTool()
                .execute(Map.of("pattern", "name", "glob", "*.java"), tempCtx).get();
        assertTrue(grep.content().contains("src/A.java:1"));
        assertFalse(grep.content().contains("B.txt"));
    }

    @Test
    @DisplayName("fs_read_image：data URI + 类型/大小守卫")
    void read_image(@TempDir Path tempDir) throws Exception {
        Files.write(tempDir.resolve("a.png"), new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        ToolRunContext tempCtx = new ToolRunContext() {
            @Override
            public String agentId() {
                return "a";
            }

            @Override
            public String sessionId() {
                return "s";
            }

            @Override
            public String cwd() {
                return tempDir.toString();
            }
        };
        ToolExecutionResult ok = new FsReadImageTool().execute(Map.of("path", "a.png"), tempCtx).get();
        assertTrue(ok.success());
        assertTrue(ok.content().startsWith("data:image/png;base64,"));

        Files.writeString(tempDir.resolve("b.txt"), "not image");
        assertEquals("UNSUPPORTED_IMAGE", new FsReadImageTool()
                .execute(Map.of("path", "b.txt"), tempCtx).get().errorCode());
    }

    @Test
    @DisplayName("web：fetch 透传端口结果，search 未配置结构化失败")
    void web_tools() throws Exception {
        IWebPort fake = new IWebPort() {
            @Override
            public Result fetch(String url) {
                return Result.ok("页面文本");
            }

            @Override
            public Result search(String query) {
                return Result.fail("搜索服务未配置", "WEB_SEARCH_NOT_CONFIGURED");
            }
        };
        assertTrue(new WebFetchTool(new com.jnf.harness.domain.tool.web.WebHostService(fake)).execute(Map.of("url", "http://x"), ctx).get().success());
        ToolExecutionResult search = new WebSearchTool(new com.jnf.harness.domain.tool.web.WebHostService(fake)).execute(Map.of("query", "hi"), ctx).get();
        assertFalse(search.success());
        assertEquals("WEB_SEARCH_NOT_CONFIGURED", search.errorCode());
    }

    @Test
    @DisplayName("ask_user_question：网关回答透传；超时结构化失败")
    void ask_user_tool() throws Exception {
        UserQuestionGateway.InMemoryUserQuestionGateway gateway =
                new UserQuestionGateway.InMemoryUserQuestionGateway();
        AskUserTool tool = new AskUserTool(gateway, 2);

        var future = tool.execute(Map.of("question", "选哪个？"), ctx);
        Thread.sleep(50);
        assertEquals(1, gateway.pendingQuestions().size());
        gateway.answer(gateway.pendingQuestions().get(0).questionId(), "选 B");
        assertTrue(future.get().success());

        ToolExecutionResult timeout = tool.execute(Map.of("question", "没人回答"), ctx).get();
        assertFalse(timeout.success());
        assertEquals("QUESTION_TIMEOUT", timeout.errorCode());
    }

    @Test
    @DisplayName("todo_write：整表覆盖 + 状态归一")
    void todo_write() throws Exception {
        TodoService service = new TodoService();
        TodoWriteTool tool = new TodoWriteTool(service);
        ToolExecutionResult result = tool.execute(Map.of("items", List.of(
                Map.of("content", "任务A", "status", "in_progress"),
                Map.of("content", "任务B", "status", "weird"))), ctx).get();

        assertTrue(result.success());
        assertEquals(2, service.list("agent-1").size());
        assertEquals("pending", service.list("agent-1").get(1).status());
    }
}

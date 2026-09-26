package com.jnf.harness.infrastructure.adapter.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jnf.harness.domain.plugin.IRemoteToolBridge;
import com.jnf.harness.model.ToolExecutionResult;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Node sidecar 桥：stdin/stdout 换行分隔 JSON-RPC 2.0。
 * <p>最小方法面：initialize / notifications/initialized / tools/list / tools/call。
 * <p>入口限定在插件安装目录内的 .js/.mjs/.cjs（防注入）。
 */
public class NodeSidecarBridge implements IRemoteToolBridge {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long REQUEST_TIMEOUT_SECONDS = 30;

    private final Path pluginDir;
    private final Path entrypoint;
    private final AtomicLong nextId = new AtomicLong(1);
    private final Map<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();

    private Process process;
    private OutputStream stdin;
    private Thread reader;

    public NodeSidecarBridge(Path pluginDir, String entrypointName) {
        this.pluginDir = pluginDir.toAbsolutePath().normalize();
        Path resolved = this.pluginDir.resolve(entrypointName).normalize();
        if (!resolved.startsWith(this.pluginDir)) {
            throw new IllegalArgumentException("入口必须位于插件目录内: " + entrypointName);
        }
        String fileName = resolved.getFileName().toString();
        if (!(fileName.endsWith(".js") || fileName.endsWith(".mjs") || fileName.endsWith(".cjs"))) {
            throw new IllegalArgumentException("入口必须是 .js/.mjs/.cjs: " + entrypointName);
        }
        this.entrypoint = resolved;
    }

    @Override
    public synchronized void start() {
        if (process != null) {
            return;
        }
        try {
            process = new ProcessBuilder("node", entrypoint.toString())
                    .directory(pluginDir.toFile())
                    .redirectErrorStream(false)
                    .start();
            stdin = process.getOutputStream();
            reader = new Thread(this::readLoop, "node-sidecar-reader");
            reader.setDaemon(true);
            reader.start();
            request("initialize", MAPPER.createObjectNode().put("protocolVersion", "2024-11-05"));
            notify("notifications/initialized", MAPPER.createObjectNode());
        } catch (Exception e) {
            stop();
            throw new IllegalStateException("Node sidecar 启动失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<RemoteTool> listTools() {
        JsonNode result = request("tools/list", MAPPER.createObjectNode());
        List<RemoteTool> tools = new ArrayList<>();
        for (JsonNode node : result.path("tools")) {
            tools.add(new RemoteTool(node.path("name").asText(""),
                    node.path("description").asText(""),
                    MAPPER.convertValue(node.path("parameters"),
                            MAPPER.getTypeFactory().constructMapType(Map.class, String.class, Object.class))));
        }
        return tools;
    }

    @Override
    public ToolExecutionResult call(String name, Map<String, Object> arguments) {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("name", name);
        params.set("arguments", MAPPER.valueToTree(arguments == null ? Map.of() : arguments));
        try {
            JsonNode result = request("tools/call", params);
            String text = result.path("content").path(0).path("text").asText(result.toString());
            boolean isError = result.path("isError").asBoolean(false);
            return isError ? ToolExecutionResult.fail(text, "REMOTE_TOOL_FAILED")
                    : ToolExecutionResult.ok(text);
        } catch (Exception e) {
            return ToolExecutionResult.fail("sidecar 调用失败: " + e.getMessage(), "REMOTE_TOOL_ERROR");
        }
    }

    @Override
    public synchronized void stop() {
        if (process != null) {
            process.destroyForcibly();
            process = null;
        }
        for (CompletableFuture<JsonNode> future : pending.values()) {
            future.completeExceptionally(new IllegalStateException("sidecar 已停止"));
        }
        pending.clear();
    }

    // ---------------- JSON-RPC ----------------

    private JsonNode request(String method, JsonNode params) {
        long id = nextId.getAndIncrement();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(id, future);
        try {
            ObjectNode rpc = MAPPER.createObjectNode();
            rpc.put("jsonrpc", "2.0");
            rpc.put("id", id);
            rpc.put("method", method);
            rpc.set("params", params);
            write(rpc);
            JsonNode response = future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (response.hasNonNull("error")) {
                throw new IllegalStateException("sidecar 错误: " + response.get("error"));
            }
            return response.path("result");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("sidecar 请求失败(" + method + "): " + e.getMessage(), e);
        } finally {
            pending.remove(id);
        }
    }

    private void notify(String method, JsonNode params) {
        ObjectNode rpc = MAPPER.createObjectNode();
        rpc.put("jsonrpc", "2.0");
        rpc.put("method", method);
        rpc.set("params", params);
        try {
            write(rpc);
        } catch (Exception e) {
            throw new IllegalStateException("sidecar 通知失败: " + e.getMessage(), e);
        }
    }

    private synchronized void write(JsonNode rpc) throws Exception {
        if (process == null || stdin == null) {
            throw new IllegalStateException("sidecar 未运行");
        }
        stdin.write((MAPPER.writeValueAsString(rpc) + "\n").getBytes(StandardCharsets.UTF_8));
        stdin.flush();
    }

    private void readLoop() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(process == null ? System.in : process.getInputStream(),
                        StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    JsonNode rpc = MAPPER.readTree(line);
                    if (rpc.hasNonNull("id") && rpc.get("id").isNumber()) {
                        CompletableFuture<JsonNode> future = pending.get(rpc.get("id").asLong());
                        if (future != null) {
                            future.complete(rpc);
                        }
                    }
                    // 通知类消息忽略
                } catch (Exception ignored) {
                    // 非 JSON 行（子进程日志）忽略
                }
            }
        } catch (Exception ignored) {
            // 进程退出
        }
    }
}

package com.jnf.harness.app.fixture;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JSON-RPC stdio 服务器夹具（Node 脚本形态，MCP/Node Bridge 测试共用）。
 */
public final class JsonRpcServerFixture {

    private JsonRpcServerFixture() {
    }

    /** 最小 JSON-RPC sidecar：initialize / tools/list / tools/call（hello 工具）。 */
    public static final String FIXTURE_JS = """
            const readline = require('readline');
            const rl = readline.createInterface({ input: process.stdin });
            rl.on('line', (line) => {
              if (!line.trim()) return;
              let req; try { req = JSON.parse(line); } catch (e) { return; }
              const respond = (result) => process.stdout.write(
                  JSON.stringify({ jsonrpc: '2.0', id: req.id, result: result }) + '\\n');
              if (req.method === 'initialize') {
                respond({ serverInfo: { name: 'jsonrpc-fixture' } });
              } else if (req.method === 'tools/list') {
                respond({ tools: [{ name: 'hello', description: '打招呼',
                  parameters: { type: 'object', properties: { who: { type: 'string' } } } }] });
              } else if (req.method === 'tools/call') {
                const who = (req.params.arguments && req.params.arguments.who) || 'world';
                respond({ content: [{ type: 'text', text: 'hello ' + who }] });
              }
            });
            """;

    public static Path writeServer(Path dir) throws Exception {
        Files.createDirectories(dir);
        Path script = dir.resolve("index.js");
        Files.writeString(script, FIXTURE_JS);
        return script;
    }
}

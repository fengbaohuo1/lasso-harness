package com.jnf.harness.domain.extension;

import com.jnf.harness.domain.plugin.IRemoteToolBridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 扩展管理服务（extension 域）：Skills / MCP Servers / CLI 配置统一管理。
 */
public class ExtensionManagementService {

    /** 技能记录。 */
    public record SkillRecord(String name, boolean enabled, String source) {
    }

    /** MCP server 记录。 */
    public record McpServerRecord(String name, List<String> command, boolean tested) {
    }

    private final Map<String, SkillRecord> skills = new ConcurrentHashMap<>();
    private final Map<String, McpServerRecord> mcpServers = new ConcurrentHashMap<>();
    private final Map<String, String> cliConfig = new ConcurrentHashMap<>();
    private final Function<List<String>, IRemoteToolBridge> bridgeFactory;

    public ExtensionManagementService(Function<List<String>, IRemoteToolBridge> bridgeFactory) {
        this.bridgeFactory = bridgeFactory;
    }

    // ---- Skills ----

    public SkillRecord installSkill(String name, String source) {
        SkillRecord record = new SkillRecord(name, true, source == null ? "" : source);
        skills.put(name, record);
        return record;
    }

    public SkillRecord setSkillEnabled(String name, boolean enabled) {
        SkillRecord record = skills.get(name);
        if (record == null) {
            throw new IllegalArgumentException("技能不存在: " + name);
        }
        SkillRecord updated = new SkillRecord(name, enabled, record.source());
        skills.put(name, updated);
        return updated;
    }

    public void removeSkill(String name) {
        skills.remove(name);
    }

    public List<SkillRecord> listSkills() {
        return new ArrayList<>(skills.values());
    }

    // ---- MCP Servers ----

    public McpServerRecord addMcpServer(String name, List<String> command) {
        McpServerRecord record = new McpServerRecord(name, List.copyOf(command), false);
        mcpServers.put(name, record);
        return record;
    }

    public void removeMcpServer(String name) {
        mcpServers.remove(name);
    }

    public List<McpServerRecord> listMcpServers() {
        return new ArrayList<>(mcpServers.values());
    }

    /** 测试连接：拉起 → tools/list → 停止；返回工具数。 */
    public int testMcpServer(String name) {
        McpServerRecord record = mcpServers.get(name);
        if (record == null) {
            throw new IllegalArgumentException("MCP server 不存在: " + name);
        }
        IRemoteToolBridge bridge = bridgeFactory.apply(record.command());
        bridge.start();
        try {
            int count = bridge.listTools().size();
            mcpServers.put(name, new McpServerRecord(name, record.command(), true));
            return count;
        } finally {
            bridge.stop();
        }
    }

    // ---- CLI ----

    public void setCli(String key, String value) {
        cliConfig.put(key, value);
    }

    public Map<String, String> cliConfig() {
        return Map.copyOf(cliConfig);
    }
}

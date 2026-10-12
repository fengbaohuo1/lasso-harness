package com.jnf.harness.plugin.sandbox;

import com.jnf.harness.model.ToolRunContext;
import com.jnf.harness.spi.PluginHookResult;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 三档权限策略（P9-2，语义对齐 dsh 沙箱）：
 * <ul>
 *   <li>{@code READ_ONLY} 只读（不限于工作区）：写类工具与 shell 一律拒绝，读不受限；</li>
 *   <li>{@code WORKSPACE_WRITE} 工作区内读写：写路径必须落在会话工作区根内；shell 受
 *       越逸模式黑名单约束（cd /、sudo …）；读不受限；</li>
 *   <li>{@code DANGER_FULL_ACCESS} 完全权限：全部放行。</li>
 * </ul>
 * 模式按会话设置（默认档来自插件配置 {@code mode}，可用端点运行期改）。
 */
public class SandboxPolicy {

    public static final String READ_ONLY = "READ_ONLY";
    public static final String WORKSPACE_WRITE = "WORKSPACE_WRITE";
    public static final String DANGER_FULL_ACCESS = "DANGER_FULL_ACCESS";
    public static final Set<String> MODES = Set.of(READ_ONLY, WORKSPACE_WRITE, DANGER_FULL_ACCESS);

    /** 写类工具（限定名取 {@code __} 后缀匹配，兼容 plugin 命名空间）。 */
    private static final Set<String> WRITE_TOOLS =
            Set.of("fs_write", "fs_edit", "str_replace_editor", "shell_execute");

    /** shell 越逸模式（WORKSPACE_WRITE 拒绝；对齐 dsh WORKSPACE_ESCAPE_PATTERNS）。 */
    private static final List<String> ESCAPE_PATTERNS =
            List.of("cd /", "cd ~", "cd $home", "sudo ", "doas ", "mount ", "umount ", "chroot ", "nsenter ");

    /** 路径类参数键。 */
    private static final Set<String> PATH_KEYS = Set.of("path", "file", "target", "paths");

    private volatile String defaultMode;
    private final Map<String, String> sessionModes = new ConcurrentHashMap<>();

    public SandboxPolicy(String defaultMode) {
        this.defaultMode = normalize(defaultMode);
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return WORKSPACE_WRITE;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (value) {
            case "READ_ONLY" -> READ_ONLY;
            case "DANGER_FULL_ACCESS", "FULL", "FULL_ACCESS" -> DANGER_FULL_ACCESS;
            default -> WORKSPACE_WRITE;
        };
    }

    public String defaultMode() {
        return defaultMode;
    }

    public void setDefaultMode(String mode) {
        this.defaultMode = normalize(mode);
    }

    public String modeOf(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return defaultMode;
        }
        return sessionModes.getOrDefault(sessionId.trim(), defaultMode);
    }

    public void setMode(String sessionId, String mode) {
        if (sessionId == null || sessionId.isBlank()) {
            setDefaultMode(mode);
            return;
        }
        sessionModes.put(sessionId.trim(), normalize(mode));
    }

    public void clearSession(String sessionId) {
        if (sessionId != null) {
            sessionModes.remove(sessionId.trim());
        }
    }

    /** PRE_TOOL_USE 评估：按会话模式裁决。 */
    public PluginHookResult evaluate(ToolRunContext context, String toolName, Map<String, Object> arguments) {
        String mode = modeOf(context == null ? null : context.sessionId());
        if (DANGER_FULL_ACCESS.equals(mode)) {
            return PluginHookResult.allow();
        }
        String base = baseName(toolName);
        if (READ_ONLY.equals(mode)) {
            if ("shell_execute".equals(base)) {
                return PluginHookResult.deny("沙箱只读模式禁止执行命令（命令可能产生副作用）");
            }
            if (WRITE_TOOLS.contains(base)) {
                return PluginHookResult.deny("沙箱只读模式禁止写类工具: " + toolName);
            }
            return PluginHookResult.allow();
        }
        // WORKSPACE_WRITE
        if ("shell_execute".equals(base)) {
            String command = String.valueOf(arguments == null ? "" : arguments.getOrDefault("command", ""));
            String lower = command.toLowerCase(Locale.ROOT);
            for (String pattern : ESCAPE_PATTERNS) {
                if (lower.contains(pattern)) {
                    return PluginHookResult.deny("命令包含越界模式 '" + pattern.trim() + "'（工作区内读写模式）");
                }
            }
            return PluginHookResult.allow();
        }
        if (WRITE_TOOLS.contains(base)) {
            Path root = workspaceRoot(context);
            for (String raw : pathArgs(arguments)) {
                Path resolved = root.resolve(raw).normalize();
                if (!resolved.startsWith(root)) {
                    return PluginHookResult.deny("沙箱工作区内读写模式禁止越界路径: " + raw);
                }
            }
        }
        return PluginHookResult.allow();
    }

    private static Path workspaceRoot(ToolRunContext context) {
        String cwd = context == null || context.cwd() == null || context.cwd().isBlank()
                ? Path.of("").toAbsolutePath().toString() : context.cwd();
        return Path.of(cwd).toAbsolutePath().normalize();
    }

    private static List<String> pathArgs(Map<String, Object> arguments) {
        if (arguments == null) {
            return List.of();
        }
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            if (!PATH_KEYS.contains(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            if (entry.getValue() instanceof Iterable<?> iterable) {
                iterable.forEach(v -> {
                    if (v != null) {
                        out.add(String.valueOf(v));
                    }
                });
            } else {
                out.add(String.valueOf(entry.getValue()));
            }
        }
        return out;
    }

    private static String baseName(String toolName) {
        if (toolName == null) {
            return "";
        }
        int idx = toolName.lastIndexOf("__");
        return idx < 0 ? toolName : toolName.substring(idx + 2);
    }
}

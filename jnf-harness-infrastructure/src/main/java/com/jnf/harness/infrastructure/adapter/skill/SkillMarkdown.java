package com.jnf.harness.infrastructure.adapter.skill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SKILL.md 文本工具：解析文件头 YAML frontmatter（仅支持简单 {@code key: value}）与正文首行描述。
 * <p>frontmatter 解析刻意保持轻量：不引入 YAML 依赖，只覆盖 SKILL.md 生态实际使用的元数据字段。
 */
final class SkillMarkdown {

    static final String FIELD_NAME = "name";
    static final String FIELD_DESCRIPTION = "description";

    private SkillMarkdown() {
    }

    /**
     * 解析结果：frontmatter 字段（键统一小写）+ 去掉 frontmatter 块后的正文。
     */
    record Parsed(Map<String, String> fields, String body) {

        Parsed {
            fields = fields == null ? Map.of() : Map.copyOf(fields);
            body = body == null ? "" : body;
        }

        /** 依次尝试多个键（小写形式），返回第一个非空值；无则返回空串。 */
        String field(String... keys) {
            for (String key : keys) {
                String value = fields.get(key);
                if (value != null && !value.isBlank()) {
                    return value.strip();
                }
            }
            return "";
        }
    }

    /**
     * 解析 SKILL.md 全文；无 frontmatter 或格式不完整时整篇作为正文，不报错。
     */
    static Parsed parse(String raw) {
        String text = raw == null ? "" : raw;
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        List<String> lines = splitLines(text);
        if (lines.isEmpty() || !"---".equals(lines.get(0).strip())) {
            return new Parsed(Map.of(), text);
        }
        int end = -1;
        for (int i = 1; i < lines.size(); i++) {
            if ("---".equals(lines.get(i).strip())) {
                end = i;
                break;
            }
        }
        if (end < 0) {
            return new Parsed(Map.of(), text);
        }
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 1; i < end; i++) {
            String line = lines.get(i);
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
            if (key.isEmpty()) {
                continue;
            }
            fields.putIfAbsent(key, unquote(line.substring(colon + 1).strip()));
        }
        String body = String.join("\n", lines.subList(end + 1, lines.size()));
        return new Parsed(fields, body);
    }

    /** frontmatter 之后第一条非空、非标题行（用作 description 兜底）。 */
    static String firstBodyLine(String body) {
        for (String line : splitLines(body)) {
            String stripped = line.strip();
            if (stripped.isEmpty() || stripped.startsWith("#")) {
                continue;
            }
            return stripped;
        }
        return "";
    }

    private static List<String> splitLines(String text) {
        if (text.isEmpty()) {
            return List.of();
        }
        String[] parts = text.split("\\R", -1);
        List<String> lines = new ArrayList<>(parts.length);
        for (String part : parts) {
            lines.add(part);
        }
        return lines;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1).strip();
            }
        }
        return value;
    }
}

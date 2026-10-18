package com.jnf.harness.domain.agent.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * 系统提示词装配：按 order 拼接各段落（插件段落、能力说明等都注册到这里）。
 * <p>段落分两类：静态段落（插件/内置注册的固定文本）与动态段落（每次装配时按 cwd 现算，
 * 例如技能目录随工作区变化）。同 order 时静态段落在前。
 */
public class SystemPromptAssembler {

    /** 提示词段落：限定名 + 拼接顺序 + 文本。 */
    public record PromptSection(String name, int order, String text) {
    }

    /** 动态提示词段落：装配时以 cwd 为入参现算文本（返回空串则不参与拼接）。 */
    public record DynamicSection(String name, int order, Function<String, String> renderer) {
    }

    private final List<PromptSection> sections = new ArrayList<>();
    private final List<DynamicSection> dynamicSections = new ArrayList<>();

    public synchronized void registerSection(PromptSection section) {
        sections.removeIf(s -> s.name().equals(section.name()));
        sections.add(section);
    }

    public synchronized void removeSection(String name) {
        sections.removeIf(s -> s.name().equals(name));
    }

    public synchronized void registerDynamicSection(DynamicSection section) {
        dynamicSections.removeIf(s -> s.name().equals(section.name()));
        dynamicSections.add(section);
    }

    public synchronized void removeDynamicSection(String name) {
        dynamicSections.removeIf(s -> s.name().equals(name));
    }

    /** 装配为最终系统提示词（无 cwd 上下文，动态段落按空 cwd 渲染）。 */
    public synchronized String assemble() {
        return assemble("");
    }

    /** 装配为最终系统提示词（按 order 升序，段落间空行分隔）。 */
    public synchronized String assemble(String cwd) {
        record Entry(int order, boolean dynamic, String text) {
        }
        List<Entry> entries = new ArrayList<>(sections.size() + dynamicSections.size());
        for (PromptSection section : sections) {
            entries.add(new Entry(section.order(), false, section.text()));
        }
        for (DynamicSection section : dynamicSections) {
            String text;
            try {
                text = section.renderer().apply(cwd == null ? "" : cwd);
            } catch (RuntimeException e) {
                // 动态段落失败不应拖垮整轮对话：跳过该段
                text = "";
            }
            entries.add(new Entry(section.order(), true, text));
        }
        return entries.stream()
                .sorted(Comparator.comparingInt(Entry::order)
                        .thenComparing(Entry::dynamic))
                .map(Entry::text)
                .filter(text -> text != null && !text.isBlank())
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
    }
}

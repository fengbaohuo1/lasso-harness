package com.jnf.harness.domain.agent.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 系统提示词装配：按 order 拼接各段落（插件段落、能力说明等都注册到这里）。
 */
public class SystemPromptAssembler {

    /** 提示词段落：限定名 + 拼接顺序 + 文本。 */
    public record PromptSection(String name, int order, String text) {
    }

    private final List<PromptSection> sections = new ArrayList<>();

    public synchronized void registerSection(PromptSection section) {
        sections.removeIf(s -> s.name().equals(section.name()));
        sections.add(section);
    }

    public synchronized void removeSection(String name) {
        sections.removeIf(s -> s.name().equals(name));
    }

    /** 装配为最终系统提示词（按 order 升序，段落间空行分隔）。 */
    public synchronized String assemble() {
        return sections.stream()
                .sorted(Comparator.comparingInt(PromptSection::order))
                .map(PromptSection::text)
                .filter(text -> text != null && !text.isBlank())
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
    }
}

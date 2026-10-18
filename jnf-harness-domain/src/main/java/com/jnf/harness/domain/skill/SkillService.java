package com.jnf.harness.domain.skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 技能服务默认实现：多 provider 合并、按来源 rank 覆盖同名技能、按需加载正文。
 */
public class SkillService implements ISkillService {

    /** 系统提示词目录最多列出的技能数（防提示词膨胀）。 */
    static final int CATALOG_MAX_SKILLS = 50;

    /** 系统提示词目录最大字符数。 */
    static final int CATALOG_MAX_CHARS = 4_000;

    private static final int TEXT_FIELD_MAX_CHARS = 200;

    private final List<ISkillProviderPort> providers;
    private final ISkillStateStore state;

    public SkillService(List<ISkillProviderPort> providers, ISkillStateStore state) {
        this.providers = List.copyOf(providers);
        this.state = state;
    }

    @Override
    public List<SkillSummary> list(String cwd) {
        Map<String, SkillCandidate> best = bestCandidates(cwd);
        return best.values().stream()
                .sorted(Comparator.comparing(SkillCandidate::name))
                .map(c -> new SkillSummary(c.name(), c.description(), c.whenToUse(),
                        c.source(), c.path(), state.isEnabled(c.name())))
                .toList();
    }

    @Override
    public Optional<SkillDefinition> get(String name, String cwd) {
        if (!SkillNames.isValid(name)) {
            return Optional.empty();
        }
        SkillCandidate best = bestCandidates(cwd).get(name);
        if (best == null) {
            return Optional.empty();
        }
        SkillDefinition found = null;
        for (ISkillProviderPort provider : providers) {
            Optional<SkillDefinition> loaded = provider.load(name, cwd);
            if (loaded.isEmpty()) {
                continue;
            }
            SkillDefinition definition = loaded.get();
            // 只接受不劣于“最佳候选来源”的定义，避免低优先级来源盖掉高优先级同名技能
            if (definition.source().rank() > best.source().rank()) {
                continue;
            }
            if (found == null || definition.source().rank() < found.source().rank()) {
                found = definition;
            }
        }
        return Optional.ofNullable(found);
    }

    @Override
    public String catalog(String cwd) {
        List<SkillSummary> enabled = list(cwd).stream().filter(SkillSummary::enabled).toList();
        if (enabled.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## Skills\n\n")
                .append("Skills are reusable instruction packages. When a task matches a skill's ")
                .append("description, call the `skill` tool with the skill name to load its full ")
                .append("instructions, then follow them.\n\n");
        int count = 0;
        for (SkillSummary skill : enabled) {
            if (count >= CATALOG_MAX_SKILLS) {
                break;
            }
            String line = "- " + skill.name() + ": " + truncate(skill.description());
            if (skill.whenToUse() != null && !skill.whenToUse().isBlank()) {
                line += " (use when: " + truncate(skill.whenToUse()) + ")";
            }
            if (sb.length() + line.length() + 1 > CATALOG_MAX_CHARS) {
                break;
            }
            sb.append(line).append('\n');
            count++;
        }
        return sb.toString().strip();
    }

    @Override
    public boolean isEnabled(String name) {
        return state.isEnabled(name);
    }

    /** 合并各 provider 的发现结果：同名技能保留 rank 最小（优先级最高）的来源。 */
    private Map<String, SkillCandidate> bestCandidates(String cwd) {
        Map<String, SkillCandidate> best = new LinkedHashMap<>();
        for (ISkillProviderPort provider : providers) {
            for (SkillCandidate candidate : provider.discover(cwd)) {
                SkillCandidate existing = best.get(candidate.name());
                if (existing == null || candidate.source().rank() < existing.source().rank()) {
                    best.put(candidate.name(), candidate);
                }
            }
        }
        return best;
    }

    private static String truncate(String text) {
        String value = text == null ? "" : text.strip();
        return value.length() <= TEXT_FIELD_MAX_CHARS
                ? value
                : value.substring(0, TEXT_FIELD_MAX_CHARS) + "…";
    }
}

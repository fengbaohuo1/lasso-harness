package com.jnf.harness.domain.skill;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 技能域单测夹具：内存 provider / state。
 */
final class SkillTestSupport {

    private SkillTestSupport() {
    }

    /** 内存技能来源：name → 元数据 + 正文。 */
    static final class FakeProvider implements ISkillProviderPort {

        private final Map<String, SkillCandidate> candidates = new LinkedHashMap<>();
        private final Map<String, String> bodies = new LinkedHashMap<>();

        FakeProvider add(String name, SkillSource source, String description, String body) {
            candidates.put(name, new SkillCandidate(name, description, "triggering task",
                    source, "/tmp/" + name + "/SKILL.md"));
            bodies.put(name, body);
            return this;
        }

        @Override
        public List<SkillCandidate> discover(String cwd) {
            return new ArrayList<>(candidates.values());
        }

        @Override
        public Optional<SkillDefinition> load(String name, String cwd) {
            SkillCandidate candidate = candidates.get(name);
            return candidate == null
                    ? Optional.empty()
                    : Optional.of(new SkillDefinition(candidate, bodies.get(name)));
        }
    }

    /** 内存启用态：默认全部启用，只记录禁用名。 */
    static final class FakeState implements ISkillStateStore {

        private final Set<String> disabled = new HashSet<>();

        @Override
        public boolean isEnabled(String name) {
            return !disabled.contains(name);
        }

        @Override
        public void setEnabled(String name, boolean enabled) {
            if (enabled) {
                disabled.remove(name);
            } else {
                disabled.add(name);
            }
        }

        @Override
        public void remove(String name) {
            disabled.remove(name);
        }

        @Override
        public Set<String> disabled() {
            return Set.copyOf(disabled);
        }
    }
}

package com.jnf.harness.domain.skill;

/**
 * 技能来源：rank 越小优先级越高；同名技能由高优先级来源覆盖。
 * <p>项目级（{@code <cwd>/.jnf/skills}）优先于用户级，用户级优先于内置包。
 */
public enum SkillSource {

    /** 项目/工作区级：{@code <cwd>/.jnf/skills}。 */
    PROJECT(100),

    /** 配置追加的根目录：{@code harness.skills.roots}。 */
    CUSTOM(300),

    /** 用户级：{@code ~/.jnf/skills}（或 {@code harness.skills.user-dir}）。 */
    USER(400),

    /** 内置/随包：{@code harness.skills.roots} 中标记为内置的目录。 */
    BUNDLED(600);

    private final int rank;

    SkillSource(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }
}

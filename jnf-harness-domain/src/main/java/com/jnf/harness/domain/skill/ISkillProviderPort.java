package com.jnf.harness.domain.skill;

import java.util.List;
import java.util.Optional;

/**
 * 技能发现/加载端口：一个 provider 负责一类根目录（或一种存储），可贡献多个来源。
 * <p>实现方不得抛出“未找到”异常——未找到返回空集合/空 Optional；只有真实 IO 失败才抛出。
 */
public interface ISkillProviderPort {

    /** 发现当前工作目录可见的技能（含来源标记），实现需自行去重（同名保留高优先级）。 */
    List<SkillCandidate> discover(String cwd);

    /** 按名加载正文（不论是否被禁用，启用态由 {@link ISkillService} 决定）。 */
    Optional<SkillDefinition> load(String name, String cwd);
}

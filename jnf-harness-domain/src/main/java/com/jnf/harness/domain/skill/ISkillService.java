package com.jnf.harness.domain.skill;

import java.util.List;
import java.util.Optional;

/**
 * 技能服务：合并多来源、按需加载正文、生成系统提示词目录。
 */
public interface ISkillService {

    /** 当前工作目录可见技能（含同名声明的优先级覆盖），按名称排序，含启用态。 */
    List<SkillSummary> list(String cwd);

    /** 按名加载正文（不论启用态；未找到返回空）。 */
    Optional<SkillDefinition> get(String name, String cwd);

    /** 生成系统提示词的 Skills 段（只列启用项；无技能返回空串）。 */
    String catalog(String cwd);

    boolean isEnabled(String name);
}

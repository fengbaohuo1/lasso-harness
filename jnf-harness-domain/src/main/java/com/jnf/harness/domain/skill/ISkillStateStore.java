package com.jnf.harness.domain.skill;

import java.util.Set;

/**
 * 技能启用态存储端口：只持久化“被禁用的技能名集合”，未记录即启用。
 */
public interface ISkillStateStore {

    boolean isEnabled(String name);

    void setEnabled(String name, boolean enabled);

    /** 遗忘该名称的启用态（删除技能时调用）。 */
    void remove(String name);

    /** 当前被禁用的技能名。 */
    Set<String> disabled();
}

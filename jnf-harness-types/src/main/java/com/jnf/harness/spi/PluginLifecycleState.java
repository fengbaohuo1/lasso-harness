package com.jnf.harness.spi;

/**
 * 插件生命周期状态。
 */
public enum PluginLifecycleState {
    /** 已登记（制品就位，未激活）。 */
    REGISTERED,
    /** 运行中（工具/提示词/Hook 已生效）。 */
    ACTIVE,
    /** 加载或运行失败。 */
    FAILED,
    /** 已停用（注册项已回收，可再启用）。 */
    DISABLED
}

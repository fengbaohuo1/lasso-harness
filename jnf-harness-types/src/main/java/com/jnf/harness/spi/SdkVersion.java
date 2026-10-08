package com.jnf.harness.spi;

/**
 * 插件 SDK 版本（P1 版本握手）。
 * <p>插件 {@code META-INF/plugin.yaml} 可声明 {@code sdkVersion: N}（缺省 1）；
 * 宿主加载时校验：插件要求的版本高于宿主支持值时明确报错，替代 {@link NoSuchMethodError} 裸炸。
 * <p>版本只增不减地演进 {@code com.jnf.harness.spi} / {@code com.jnf.harness.model} 的契约面。
 */
public final class SdkVersion {

    /** 宿主当前支持的 SDK 版本。 */
    public static final int CURRENT = 1;

    private SdkVersion() {
    }
}

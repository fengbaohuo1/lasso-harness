package com.jnf.harness.spi.host;

/**
 * 宿主服务：Web 抓取与搜索通道（P1 宿主服务查询扩展点）。
 * <p>工具插件经 {@code PluginContext.lookupService(WebService.class)} 获取；
 * 上游凭据、网络策略与未配置降级由宿主处理。
 */
public interface WebService {

    /** 抓取 URL 内容（纯文本）。 */
    WebOutcome fetch(String url);

    /** 搜索（宿主未配置搜索服务时 outcome 带 notConfigured 错误码）。 */
    WebOutcome search(String query);

    /**
     * 通用结果：success=false 时 content 为错误信息，errorCode 为错误分类。
     */
    record WebOutcome(boolean success, String content, String errorCode) {

        public static WebOutcome ok(String content) {
            return new WebOutcome(true, content, null);
        }

        public static WebOutcome fail(String message, String errorCode) {
            return new WebOutcome(false, message, errorCode);
        }
    }
}

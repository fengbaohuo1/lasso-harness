package com.jnf.harness.domain.tool.web;

/**
 * Web 能力端口：抓取与搜索（infra 提供实现）。
 */
public interface IWebPort {

    /** 抓取 URL 内容（返回纯文本）。 */
    Result fetch(String url);

    /** 搜索（依赖外部搜索服务；未配置时返回 notConfigured）。 */
    Result search(String query);

    record Result(boolean success, String content, String errorCode) {
        public static Result ok(String content) {
            return new Result(true, content, null);
        }

        public static Result fail(String message, String errorCode) {
            return new Result(false, message, errorCode);
        }
    }
}

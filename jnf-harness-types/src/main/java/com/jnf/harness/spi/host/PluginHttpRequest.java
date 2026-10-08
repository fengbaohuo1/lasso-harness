package com.jnf.harness.spi.host;

import java.util.Map;

/**
 * 插件 HTTP 端点请求（P1/SD3）：宿主把 {@code /api/harness/plugins/{id}/api/**} 的调用转交插件。
 *
 * @param method    HTTP 方法（大写）
 * @param path      端点内相对路径（注册时的 path，无前导宿主前缀）
 * @param query     查询参数
 * @param bodyJson  请求体原文（JSON 字符串；无体为 null）
 */
public record PluginHttpRequest(String method, String path, Map<String, String> query, String bodyJson) {
}

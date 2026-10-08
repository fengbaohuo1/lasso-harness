package com.jnf.harness.domain.tool.web;

import com.jnf.harness.spi.host.WebService;

/**
 * {@link WebService} 宿主实现：IWebPort 委托（P1 宿主服务）。
 * <p>web_fetch / web_search 工具已外移为官方插件（jnf-harness-plugin-web），
 * 经 {@code PluginContext.lookupService(WebService.class)} 取得本服务；
 * 上游凭据与未配置降级留在宿主侧。
 */
public class WebHostService implements WebService {

    private final IWebPort webPort;

    public WebHostService(IWebPort webPort) {
        this.webPort = webPort;
    }

    @Override
    public WebOutcome fetch(String url) {
        IWebPort.Result result = webPort.fetch(url);
        return result.success() ? WebOutcome.ok(result.content())
                : WebOutcome.fail(result.content(), result.errorCode());
    }

    @Override
    public WebOutcome search(String query) {
        IWebPort.Result result = webPort.search(query);
        return result.success() ? WebOutcome.ok(result.content())
                : WebOutcome.fail(result.content(), result.errorCode());
    }
}

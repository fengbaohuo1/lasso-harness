package com.jnf.harness.spi.host;

import java.util.List;

/**
 * 宿主服务：跨会话历史检索（A3，session_search 工具插件经
 * {@code PluginContext.lookupService(SessionSearchService.class)} 取用）。
 * <p>事件存储与扫描策略留在宿主侧，插件只做呈现。
 */
public interface SessionSearchService {

    /**
     * 按关键词搜索历史消息（大小写不敏感）。
     *
     * @param query  关键词
     * @param maxHits 最多返回条数
     */
    List<SearchHit> search(String query, int maxHits);

    /** 命中项：会话 id + 命中文本。 */
    record SearchHit(String sessionId, String text) {
    }
}

package com.jnf.harness.domain.runtime.model;

import java.util.List;

/**
 * 模型发现端口：探测上游可用模型列表。
 */
public interface IModelDiscoveryPort {

    List<String> discover(String baseUrl, String apiKey, String protocol);
}

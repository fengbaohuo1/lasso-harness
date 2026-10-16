package com.jnf.harness.domain.runtime.setting;

import java.util.List;

/**
 * 模型条目仓储端口（P11-1，infra 用 MyBatis 实现）。
 */
public interface IModelRepository {

    void upsert(ModelEntity entity);

    void delete(String channelCode, String modelCode);

    void deleteByChannel(String channelCode);

    List<ModelEntity> findAll();

    List<ModelEntity> findByChannel(String channelCode);

    /** 清空该渠道的全部 default 标记。 */
    void clearDefault(String channelCode);

    /** 置指定模型为默认（调用方负责先 {@link #clearDefault(String)}）。 */
    void markDefault(String channelCode, String modelCode);
}

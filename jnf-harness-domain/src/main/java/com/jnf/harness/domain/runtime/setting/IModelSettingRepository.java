package com.jnf.harness.domain.runtime.setting;

import java.util.List;
import java.util.Optional;

/**
 * 模型渠道仓储端口（infra 用 MyBatis 实现）。
 */
public interface IModelSettingRepository {

    void upsert(ModelSettingEntity entity);

    void delete(String channelCode);

    List<ModelSettingEntity> findAll();

    Optional<ModelSettingEntity> findActive();

    /** 唯一激活：先全量取消，再激活指定渠道。 */
    void activate(String channelCode);
}

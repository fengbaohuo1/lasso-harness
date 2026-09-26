package com.jnf.harness.infrastructure.dao;

import com.jnf.harness.infrastructure.dao.po.ModelSettingPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 模型渠道 DAO（harness_model_setting）。
 */
@Mapper
public interface ModelSettingDao {

    @Insert("INSERT INTO harness_model_setting "
            + "(channel_code, display_name, provider_code, model_code, base_url, api_key_ref, protocol, enabled, active, updated_at) "
            + "VALUES (#{channelCode}, #{displayName}, #{providerCode}, #{modelCode}, #{baseUrl}, #{apiKeyRef}, "
            + "#{protocol}, #{enabled}, FALSE, CURRENT_TIMESTAMP) "
            + "ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), provider_code = VALUES(provider_code), "
            + "model_code = VALUES(model_code), base_url = VALUES(base_url), api_key_ref = VALUES(api_key_ref), "
            + "protocol = VALUES(protocol), enabled = VALUES(enabled), updated_at = CURRENT_TIMESTAMP")
    void upsert(ModelSettingPO po);

    @Delete("DELETE FROM harness_model_setting WHERE channel_code = #{channelCode}")
    void deleteByCode(@Param("channelCode") String channelCode);

    @Select("SELECT * FROM harness_model_setting ORDER BY channel_code")
    List<ModelSettingPO> findAll();

    @Select("SELECT * FROM harness_model_setting WHERE active = TRUE LIMIT 1")
    ModelSettingPO findActive();

    @Update("UPDATE harness_model_setting SET active = FALSE")
    void clearActive();

    @Update("UPDATE harness_model_setting SET active = TRUE WHERE channel_code = #{channelCode}")
    void setActive(@Param("channelCode") String channelCode);
}

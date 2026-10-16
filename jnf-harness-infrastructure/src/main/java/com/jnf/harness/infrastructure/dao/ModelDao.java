package com.jnf.harness.infrastructure.dao;

import com.jnf.harness.infrastructure.dao.po.ModelPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 模型条目 DAO（P11-1，表 harness_model）。
 * <p>{@code is_default} 不参与 upsert 更新——默认模型只由 {@link #clearDefault}/{@link #markDefault} 维护，
 * 避免「保存模型」把默认标记冲掉。
 */
@Mapper
public interface ModelDao {

    @Insert("INSERT INTO harness_model "
            + "(channel_code, model_code, display_name, context_window, max_output_tokens, input_modalities, "
            + "output_modalities, enabled, is_default, sort_order, updated_at) "
            + "VALUES (#{channelCode}, #{modelCode}, #{displayName}, #{contextWindow}, #{maxOutputTokens}, "
            + "#{inputModalities}, #{outputModalities}, #{enabled}, #{defaultFlag}, #{sortOrder}, CURRENT_TIMESTAMP) "
            + "ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), context_window = VALUES(context_window), "
            + "max_output_tokens = VALUES(max_output_tokens), input_modalities = VALUES(input_modalities), "
            + "output_modalities = VALUES(output_modalities), enabled = VALUES(enabled), "
            + "sort_order = VALUES(sort_order), updated_at = CURRENT_TIMESTAMP")
    void upsert(ModelPO po);

    @Delete("DELETE FROM harness_model WHERE channel_code = #{channelCode} AND model_code = #{modelCode}")
    void deleteOne(@Param("channelCode") String channelCode, @Param("modelCode") String modelCode);

    @Delete("DELETE FROM harness_model WHERE channel_code = #{channelCode}")
    void deleteByChannel(@Param("channelCode") String channelCode);

    /*
     * 列名 is_default 必须显式映射：开启 mapUnderscoreToCamelCase 后 MyBatis 会把它猜成属性
     * `isDefault`，而 PO 里叫 `defaultFlag`（避开 JavaBeans 的 default 属性名），猜不中就会恒为 false。
     */
    @Select("SELECT * FROM harness_model ORDER BY channel_code, sort_order, model_code")
    @Results(id = "modelPo", value = {
            @Result(column = "is_default", property = "defaultFlag"),
    })
    List<ModelPO> findAll();

    @Select("SELECT * FROM harness_model WHERE channel_code = #{channelCode} ORDER BY sort_order, model_code")
    @Results(id = "modelPoByChannel", value = {
            @Result(column = "is_default", property = "defaultFlag"),
    })
    List<ModelPO> findByChannel(@Param("channelCode") String channelCode);

    @Update("UPDATE harness_model SET is_default = FALSE WHERE channel_code = #{channelCode}")
    void clearDefault(@Param("channelCode") String channelCode);

    @Update("UPDATE harness_model SET is_default = TRUE WHERE channel_code = #{channelCode} AND model_code = #{modelCode}")
    void markDefault(@Param("channelCode") String channelCode, @Param("modelCode") String modelCode);
}

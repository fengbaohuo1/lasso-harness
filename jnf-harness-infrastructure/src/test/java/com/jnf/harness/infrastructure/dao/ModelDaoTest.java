package com.jnf.harness.infrastructure.dao;

import com.jnf.harness.infrastructure.dao.po.ModelPO;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型条目 DAO 映射（P11-1）。
 * <p>
 * 这个测试盯的是一个真踩过的坑：表里列名是 {@code is_default}，而 PO 的字段叫 {@code defaultFlag}
 * （JavaBeans 不允许属性名 default）。开 {@code map-underscore-to-camel-case} 后 MyBatis 会把列猜成属性
 * {@code isDefault}，猜不中就**静默**把默认标记读成 false——接口返回一切正常，只有 UI 上「默认模型」徽标不见了。
 * 所以这里必须让 SELECT 的映射结果与标记写入一致。
 */
class ModelDaoTest {

    private SqlSessionFactory factory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:modeldao-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE harness_model ("
                    + "channel_code VARCHAR(64) NOT NULL, "
                    + "model_code VARCHAR(128) NOT NULL, "
                    + "display_name VARCHAR(128), "
                    + "context_window INT, "
                    + "max_output_tokens INT, "
                    + "input_modalities VARCHAR(64) NOT NULL DEFAULT 'text', "
                    + "output_modalities VARCHAR(64) NOT NULL DEFAULT 'text', "
                    + "enabled BOOLEAN NOT NULL DEFAULT TRUE, "
                    + "is_default BOOLEAN NOT NULL DEFAULT FALSE, "
                    + "sort_order INT NOT NULL DEFAULT 0, "
                    + "updated_at TIMESTAMP(3), "
                    + "PRIMARY KEY (channel_code, model_code))");
        }
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(ModelDao.class);
        factory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @Test
    @DisplayName("is_default 列能映射回 defaultFlag（否则默认徽标恒不可见）")
    void defaultFlag_isMappedBack() {
        try (SqlSession session = factory.openSession(true)) {
            ModelDao dao = session.getMapper(ModelDao.class);
            dao.upsert(po("default", "default-model", false));
            dao.upsert(po("default", "other-model", false));

            dao.clearDefault("default");
            dao.markDefault("default", "other-model");

            List<ModelPO> rows = dao.findByChannel("default");
            assertEquals(2, rows.size());
            assertTrue(rows.stream()
                            .filter(row -> "other-model".equals(row.getModelCode()))
                            .findFirst().orElseThrow().isDefaultFlag(),
                    "markDefault 之后该行必须读回 is_default = true");
            assertFalse(rows.stream()
                            .filter(row -> "default-model".equals(row.getModelCode()))
                            .findFirst().orElseThrow().isDefaultFlag());

            assertTrue(dao.findAll().stream()
                            .filter(row -> "other-model".equals(row.getModelCode()))
                            .findFirst().orElseThrow().isDefaultFlag(),
                    "findAll 走的是另一条 @Select，同样要映射 is_default");
        }
    }

    @Test
    @DisplayName("upsert 不冲掉默认标记（保存模型不该把默认改没）")
    void upsert_keepsDefaultFlag() {
        try (SqlSession session = factory.openSession(true)) {
            ModelDao dao = session.getMapper(ModelDao.class);
            dao.upsert(po("default", "default-model", false));
            dao.clearDefault("default");
            dao.markDefault("default", "default-model");

            // 再存一次同一模型（改显示名），is_default 不在 ON DUPLICATE KEY UPDATE 列里
            dao.upsert(po("default", "default-model", false));

            ModelPO row = dao.findByChannel("default").get(0);
            assertEquals("改名后", row.getDisplayName());
            assertTrue(row.isDefaultFlag(), "upsert 后默认标记必须保留");
        }
    }

    private static ModelPO po(String channelCode, String modelCode, boolean defaultFlag) {
        ModelPO po = new ModelPO();
        po.setChannelCode(channelCode);
        po.setModelCode(modelCode);
        po.setDisplayName("改名后");
        po.setContextWindow(128000);
        po.setMaxOutputTokens(8192);
        po.setInputModalities("text");
        po.setOutputModalities("text");
        po.setEnabled(true);
        po.setDefaultFlag(defaultFlag);
        po.setSortOrder(0);
        return po;
    }
}

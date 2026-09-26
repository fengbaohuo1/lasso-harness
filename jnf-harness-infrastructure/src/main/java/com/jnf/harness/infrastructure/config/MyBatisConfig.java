package com.jnf.harness.infrastructure.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 装配：扫描 DAO 接口。
 */
@Configuration
@MapperScan("com.jnf.harness.infrastructure.dao")
public class MyBatisConfig {
}

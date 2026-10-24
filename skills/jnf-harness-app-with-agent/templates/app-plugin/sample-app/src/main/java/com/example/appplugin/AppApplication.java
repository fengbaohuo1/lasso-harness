package com.example.appplugin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用启动类（Spring Boot 3.3 / Java 17）。
 * <p>启动：{@code java -jar sample-app/target/sample-app.jar --server.port=18081}
 * <p>端口显式传：环境变量 SERVER_PORT 会覆盖 application.yml，别依赖默认值。
 */
@SpringBootApplication
public class AppApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppApplication.class, args);
    }
}

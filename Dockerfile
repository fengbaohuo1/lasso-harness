# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY . .
RUN mvn -q -B clean package -DskipTests

# ---- 运行阶段 ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /build/jnf-harness-app/target/jnf-harness-app.jar /app/jnf-harness-app.jar
# P4 多 jar：官方插件独立 JAR，经 plugins/ 目录动态加载（内核 jar 同级自动发现）
COPY --from=build /build/jnf-harness-app/target/plugins /app/plugins
# R9：密钥经环境变量注入，镜像内无任何凭据
ENV SPRING_DATASOURCE_URL="jdbc:h2:file:/app/data/jnf-harness;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE"
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/jnf-harness-app.jar", "--spring.profiles.active=standalone"]

package com.jnf.harness.trigger.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * Web MVC 配置：鉴权拦截器挂载 + 本地回环放开 CORS（服务只绑回环）。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final List<String> apiKeys;

    public WebMvcConfig(@Value("${harness.auth.api-keys:}") String apiKeysRaw) {
        this.apiKeys = apiKeysRaw == null || apiKeysRaw.isBlank()
                ? List.of()
                : Arrays.stream(apiKeysRaw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ApiKeyAuthInterceptor(apiKeys)).addPathPatterns("/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
}

package com.dust.wxclawbackfront.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfig(@Value("${wxclaw.api.cors.allowed-origins:http://localhost:3000}") String[] allowedOrigins) {
        if (allowedOrigins == null || allowedOrigins.length == 0) {
            throw new IllegalStateException("CORS allowed origins must be configured");
        }
        this.allowedOrigins = Arrays.stream(allowedOrigins)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toArray(String[]::new);
        if (this.allowedOrigins.length == 0 || Arrays.asList(this.allowedOrigins).contains("*")) {
            throw new IllegalStateException("CORS allowed origins must be explicit and cannot contain '*'");
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

}

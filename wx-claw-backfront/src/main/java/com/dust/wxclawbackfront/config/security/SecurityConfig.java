package com.dust.wxclawbackfront.config.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityConfig {

    @Bean
    public FilterRegistrationBean<RequestBodySizeLimitFilter> requestBodySizeLimitFilter(
            @Value("${wxclaw.security.max-json-body-bytes:1048576}") long maxJsonBodyBytes) {
        FilterRegistrationBean<RequestBodySizeLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestBodySizeLimitFilter(maxJsonBodyBytes));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(-100);
        registration.setName("requestBodySizeLimitFilter");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilter() {
        FilterRegistrationBean<SecurityHeadersFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new SecurityHeadersFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(0);
        registration.setName("securityHeadersFilter");
        return registration;
    }

    @Bean
    @ConditionalOnProperty(prefix = "wxclaw.api", name = "auth-enabled", havingValue = "true")
    public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyFilter(ApiKeyAuthFilter filter) {
        FilterRegistrationBean<ApiKeyAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/api/ai/*", "/api/admin/*", "/api/internal/*", "/api/user/*");
        registration.setOrder(1);
        registration.setName("apiKeyAuthFilter");
        return registration;
    }
}

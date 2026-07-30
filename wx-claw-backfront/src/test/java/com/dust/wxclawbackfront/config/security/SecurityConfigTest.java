package com.dust.wxclawbackfront.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityConfigTest {

    @Test
    void protectsInternalWorkerEndpointsWithApiKeyAuthentication() {
        ApiKeyAuthFilter filter = mock(ApiKeyAuthFilter.class);

        FilterRegistrationBean<ApiKeyAuthFilter> registration = new SecurityConfig().apiKeyFilter(filter);

        assertThat(registration.getUrlPatterns())
                .contains("/api/ai/*", "/api/admin/*", "/api/internal/*");
    }
}

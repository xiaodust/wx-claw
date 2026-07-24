package com.dust.wxclawbackfront.config.security;

import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.dust.wxclawbackfront.tenancy.security.TenantApiKeyAuthenticator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "wxclaw.api", name = "auth-enabled", havingValue = "true")
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final TenantApiKeyAuthenticator authenticator;

    public ApiKeyAuthFilter(TenantApiKeyAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain chain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // 健康检查端点跳过认证
        if (path.equals("/actuator/health") || path.equals("/actuator/info")) {
            chain.doFilter(request, response);
            return;
        }

        // OPTIONS 请求跳过认证（CORS 预检请求）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String requestApiKey = request.getHeader("X-API-Key");

        TenantContext context = authenticator.authenticate(requestApiKey);
        if (context == null) {
            log.warn("API 认证失败: path={}, remoteAddr={}", path, request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"Invalid or missing API key\"}");
            return;
        }
        try {
            TenantContextHolder.set(context);
            chain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }
}

package com.dust.wxclawbackfront.config.security;

import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.dust.wxclawbackfront.tenancy.service.TenantAuthService;
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

/**
 * REST API 的租户上下文入口。
 *
 * <p>过滤器把 {@code X-API-Key} 认证结果写入当前线程，后续 Controller、Service 和
 * JPA 实体监听器均从同一上下文读取租户身份。请求结束时必须清理 ThreadLocal，
 * 防止 Tomcat 工作线程复用时发生跨租户上下文泄漏。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "wxclaw.api", name = "auth-enabled", havingValue = "true")
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final TenantApiKeyAuthenticator authenticator;
    private final TenantAuthService sessionAuthenticator;

    public ApiKeyAuthFilter(TenantApiKeyAuthenticator authenticator, TenantAuthService sessionAuthenticator) {
        this.authenticator = authenticator;
        this.sessionAuthenticator = sessionAuthenticator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain chain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // 健康检查不读取租户数据，允许基础设施在无凭据时探活。
        if (path.equals("/actuator/health") || path.equals("/actuator/info")) {
            chain.doFilter(request, response);
            return;
        }

        // CORS 预检只协商跨域策略，不进入业务处理链。
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String requestApiKey = request.getHeader("X-API-Key");

        // 优先按 API Key 认证；失败时回退到控制台会话 token（sess_ 前缀）。
        TenantContext context = authenticator.authenticate(requestApiKey);
        if (context == null) {
            context = sessionAuthenticator.authenticateSession(requestApiKey);
        }
        if (context == null) {
            log.warn("API 认证失败: path={}, remoteAddr={}", path, request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"Invalid or missing API key\"}");
            return;
        }
        try {
            // 从这一行开始，下游代码才能安全调用 TenantContextHolder.require()。
            TenantContextHolder.set(context);
            chain.doFilter(request, response);
        } finally {
            // Tomcat 会复用请求线程，认证成功后的所有退出路径都必须清理上下文。
            TenantContextHolder.clear();
        }
    }
}

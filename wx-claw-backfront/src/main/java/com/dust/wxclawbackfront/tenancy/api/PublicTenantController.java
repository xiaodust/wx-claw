package com.dust.wxclawbackfront.tenancy.api;

import com.dust.wxclawbackfront.config.security.SessionCookieService;
import com.dust.wxclawbackfront.tenancy.api.PublicTenantDtos.ApiError;
import com.dust.wxclawbackfront.tenancy.api.PublicTenantDtos.RegisterTenantRequest;
import com.dust.wxclawbackfront.tenancy.api.PublicTenantDtos.RegisteredTenant;
import com.dust.wxclawbackfront.tenancy.service.TenantRegistrationException;
import com.dust.wxclawbackfront.tenancy.service.TenantRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户自助注册公开接口。
 *
 * <p>路径使用 {@code /api/public/*}，不在 ApiKeyAuthFilter 的拦截范围内，
 * 因此无需凭据即可访问；注册成功返回仅展示一次的 API Key。</p>
 */
@RestController
@RequestMapping("/api/public/tenants")
@RequiredArgsConstructor
public class PublicTenantController {

    private final TenantRegistrationService registrationService;
    private final SessionCookieService sessionCookieService;

    @Value("${wxclaw.security.trust-forwarded-headers:false}")
    private boolean trustForwardedHeaders;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody(required = false) RegisterTenantRequest request,
                                      HttpServletRequest httpRequest,
                                      HttpServletResponse httpResponse) {
        try {
            RegisteredTenant tenant = registrationService.register(request, clientIp(httpRequest));
            if (tenant.sessionToken() != null && !tenant.sessionToken().isBlank()) {
                sessionCookieService.setSessionCookie(httpResponse, tenant.sessionToken(), tenant.sessionExpiresAt());
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(tenant);
        } catch (TenantRegistrationException ex) {
            return ResponseEntity.status(ex.status())
                    .body(new ApiError(ex.errorCode(), ex.getMessage()));
        }
    }

    private String clientIp(HttpServletRequest request) {
        if (trustForwardedHeaders) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                int comma = forwarded.indexOf(',');
                return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
            }
        }
        return request.getRemoteAddr();
    }
}

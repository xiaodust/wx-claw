package com.dust.wxclawbackfront.tenancy.api;

import com.dust.wxclawbackfront.tenancy.api.PublicTenantDtos.ApiError;
import com.dust.wxclawbackfront.tenancy.api.PublicTenantDtos.AuthResult;
import com.dust.wxclawbackfront.tenancy.api.PublicTenantDtos.LoginRequest;
import com.dust.wxclawbackfront.tenancy.service.TenantAuthService;
import com.dust.wxclawbackfront.tenancy.service.TenantRegistrationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 控制台账号登录公开接口（/api/public/* 不经过 API Key 过滤器）。
 */
@RestController
@RequestMapping("/api/public/auth")
@RequiredArgsConstructor
public class PublicAuthController {

    private final TenantAuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody(required = false) LoginRequest request,
                                   HttpServletRequest httpRequest) {
        try {
            if (request == null || request.username() == null || request.username().isBlank()
                    || request.password() == null || request.password().isBlank()) {
                throw new TenantRegistrationException("VALIDATION_ERROR", "请输入用户名和密码",
                        HttpStatus.BAD_REQUEST);
            }
            AuthResult result = authService.login(request.username(), request.password(), clientIp(httpRequest));
            return ResponseEntity.ok(result);
        } catch (TenantRegistrationException ex) {
            return ResponseEntity.status(ex.status())
                    .body(new ApiError(ex.errorCode(), ex.getMessage()));
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}

package com.dust.wxclawbackfront.tenancy.api;

import com.dust.wxclawbackfront.tenancy.api.PublicTenantDtos.ApiError;
import com.dust.wxclawbackfront.tenancy.api.PublicTenantDtos.AuthResult;
import com.dust.wxclawbackfront.tenancy.api.PublicTenantDtos.EmailCodeRequest;
import com.dust.wxclawbackfront.tenancy.api.PublicTenantDtos.ForgotPasswordRequest;
import com.dust.wxclawbackfront.tenancy.api.PublicTenantDtos.LoginRequest;
import com.dust.wxclawbackfront.tenancy.api.PublicTenantDtos.OperationResult;
import com.dust.wxclawbackfront.tenancy.api.PublicTenantDtos.ResetPasswordRequest;
import com.dust.wxclawbackfront.tenancy.service.PasswordResetService;
import com.dust.wxclawbackfront.tenancy.service.EmailVerificationService;
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
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;

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

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody(required = false) ForgotPasswordRequest request,
                                            HttpServletRequest httpRequest) {
        try {
            if (request == null || request.usernameOrEmail() == null || request.usernameOrEmail().isBlank()) {
                throw new TenantRegistrationException("VALIDATION_ERROR", "请输入用户名或邮箱",
                        HttpStatus.BAD_REQUEST);
            }
            passwordResetService.requestReset(request.usernameOrEmail(), clientIp(httpRequest));
            // 账号不存在也返回同一提示，避免泄露账号是否存在。
            return ResponseEntity.ok(new OperationResult("如果账号存在，重置链接已发送到对应邮箱"));
        } catch (TenantRegistrationException ex) {
            return ResponseEntity.status(ex.status())
                    .body(new ApiError(ex.errorCode(), ex.getMessage()));
        }
    }

    @PostMapping("/email-code")
    public ResponseEntity<?> sendEmailCode(@RequestBody(required = false) EmailCodeRequest request,
                                           HttpServletRequest httpRequest) {
        try {
            if (request == null || request.email() == null || request.email().isBlank()) {
                throw new TenantRegistrationException("VALIDATION_ERROR", "请输入邮箱",
                        HttpStatus.BAD_REQUEST);
            }
            emailVerificationService.sendCode(request.email(), request.purpose(), clientIp(httpRequest));
            return ResponseEntity.ok(new OperationResult("验证码已发送，请查收邮件（注意垃圾箱）"));
        } catch (TenantRegistrationException ex) {
            return ResponseEntity.status(ex.status())
                    .body(new ApiError(ex.errorCode(), ex.getMessage()));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody(required = false) ResetPasswordRequest request) {
        try {
            if (request == null || request.token() == null || request.token().isBlank()) {
                throw new TenantRegistrationException("INVALID_TOKEN", "重置链接无效或已过期",
                        HttpStatus.BAD_REQUEST);
            }
            passwordResetService.resetPassword(request.token(), request.newPassword());
            return ResponseEntity.ok(new OperationResult("密码已重置，请使用新密码登录"));
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

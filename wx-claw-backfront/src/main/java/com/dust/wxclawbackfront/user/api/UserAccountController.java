package com.dust.wxclawbackfront.user.api;

import com.dust.wxclawbackfront.config.security.SessionCookieService;
import com.dust.wxclawbackfront.tenancy.TenantAccessGuard;
import com.dust.wxclawbackfront.tenancy.api.PublicTenantDtos.OperationResult;
import com.dust.wxclawbackfront.tenancy.api.PublicTenantDtos;
import com.dust.wxclawbackfront.tenancy.entity.TenantAccount;
import com.dust.wxclawbackfront.tenancy.service.TenantAuthService;
import com.dust.wxclawbackfront.tenancy.service.TenantRegistrationException;
import com.dust.wxclawbackfront.user.api.dto.UserDtos.AccountInfo;
import com.dust.wxclawbackfront.user.api.dto.UserDtos.ChangePasswordRequest;
import com.dust.wxclawbackfront.user.api.dto.UserDtos.SetupAccountRequest;
import com.dust.wxclawbackfront.user.api.dto.UserDtos.SetupAccountResult;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户控制台账号接口：修改密码等。
 */
@RestController
@RequestMapping("/api/user/account")
@RequiredArgsConstructor
public class UserAccountController {

    private final TenantAuthService authService;
    private final TenantAccessGuard accessGuard;
    private final SessionCookieService sessionCookieService;

    @GetMapping
    public AccountInfo account() {
        accessGuard.requireScope("account:read");
        TenantAccount account = authService.consoleAccount();
        return new AccountInfo(account == null ? null : account.getUsername(),
                account == null ? null : account.getContactEmail(), account != null);
    }

    @PostMapping("/setup")
    public ResponseEntity<?> setupAccount(@RequestBody(required = false) SetupAccountRequest request,
                                          HttpServletResponse response) {
        accessGuard.requireScope("account:write");
        try {
            if (request == null || request.username() == null || request.username().isBlank()
                    || request.contactEmail() == null || request.contactEmail().isBlank()
                    || request.emailCode() == null || request.emailCode().isBlank()
                    || request.password() == null || request.password().isBlank()) {
                throw new TenantRegistrationException("VALIDATION_ERROR", "请完整填写用户名、邮箱、验证码和密码",
                        HttpStatus.BAD_REQUEST);
            }
            TenantAuthService.AccountIssue issue = authService.setupAccount(
                    request.username(), request.password(), request.contactEmail(), request.emailCode());
            sessionCookieService.setSessionCookie(response, issue.sessionToken(), issue.expiresAt());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new SetupAccountResult(issue.username(), issue.sessionToken(), issue.expiresAt()));
        } catch (TenantRegistrationException ex) {
            return ResponseEntity.status(ex.status())
                    .body(new PublicTenantDtos.ApiError(ex.errorCode(), ex.getMessage()));
        }
    }

    @PostMapping("/password")
    public ResponseEntity<?> changePassword(@RequestBody(required = false) ChangePasswordRequest request) {
        accessGuard.requireScope("account:write");
        try {
            if (request == null || request.oldPassword() == null || request.oldPassword().isBlank()
                    || request.newPassword() == null || request.newPassword().isBlank()) {
                throw new TenantRegistrationException("VALIDATION_ERROR", "请输入旧密码和新密码",
                        org.springframework.http.HttpStatus.BAD_REQUEST);
            }
            authService.changePassword(request.oldPassword(), request.newPassword());
            // 修改后全部会话已吊销，前端应引导重新登录。
            return ResponseEntity.ok(new OperationResult("密码已修改，请重新登录"));
        } catch (TenantRegistrationException ex) {
            return ResponseEntity.status(ex.status())
                    .body(new PublicTenantDtos.ApiError(ex.errorCode(), ex.getMessage()));
        }
    }
}

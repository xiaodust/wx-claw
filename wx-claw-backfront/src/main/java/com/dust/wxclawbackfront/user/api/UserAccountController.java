package com.dust.wxclawbackfront.user.api;

import com.dust.wxclawbackfront.tenancy.TenantAccessGuard;
import com.dust.wxclawbackfront.tenancy.api.PublicTenantDtos.OperationResult;
import com.dust.wxclawbackfront.tenancy.api.PublicTenantDtos;
import com.dust.wxclawbackfront.tenancy.service.TenantAuthService;
import com.dust.wxclawbackfront.tenancy.service.TenantRegistrationException;
import com.dust.wxclawbackfront.user.api.dto.UserDtos.ChangePasswordRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

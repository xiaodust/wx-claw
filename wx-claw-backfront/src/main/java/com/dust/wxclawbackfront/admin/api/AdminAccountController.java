package com.dust.wxclawbackfront.admin.api;

import com.dust.wxclawbackfront.admin.api.dto.AdminDtos.ChangeAdminPasswordRequest;
import com.dust.wxclawbackfront.admin.security.AdminAccessGuard;
import com.dust.wxclawbackfront.tenancy.api.PublicTenantDtos.ApiError;
import com.dust.wxclawbackfront.tenancy.api.PublicTenantDtos.OperationResult;
import com.dust.wxclawbackfront.tenancy.service.AdminAuthService;
import com.dust.wxclawbackfront.tenancy.service.TenantRegistrationException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端账号接口：修改密码等（需管理员会话登录）。
 */
@RestController
@RequestMapping("/api/admin/account")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AdminAuthService adminAuthService;
    private final AdminAccessGuard accessGuard;

    @PostMapping("/password")
    public ResponseEntity<?> changePassword(@RequestBody(required = false) ChangeAdminPasswordRequest request) {
        accessGuard.requireWrite();
        try {
            if (request == null || request.oldPassword() == null || request.oldPassword().isBlank()
                    || request.newPassword() == null || request.newPassword().isBlank()) {
                throw new TenantRegistrationException("VALIDATION_ERROR", "请输入旧密码和新密码",
                        HttpStatus.BAD_REQUEST);
            }
            adminAuthService.changePassword(request.oldPassword(), request.newPassword());
            // 修改后全部管理会话已吊销，前端应引导重新登录。
            return ResponseEntity.ok(new OperationResult("密码已修改，请重新登录"));
        } catch (TenantRegistrationException ex) {
            return ResponseEntity.status(ex.status())
                    .body(new ApiError(ex.errorCode(), ex.getMessage()));
        }
    }
}

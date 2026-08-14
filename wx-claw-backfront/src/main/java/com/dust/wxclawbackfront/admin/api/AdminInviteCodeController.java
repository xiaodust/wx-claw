package com.dust.wxclawbackfront.admin.api;

import com.dust.wxclawbackfront.admin.api.dto.AdminDtos.GenerateInviteCodesRequest;
import com.dust.wxclawbackfront.admin.api.dto.AdminDtos.InviteCode;
import com.dust.wxclawbackfront.tenancy.TenantAccessGuard;
import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.dust.wxclawbackfront.tenancy.service.InviteCodeService;
import com.dust.wxclawbackfront.tenancy.service.TenantRegistrationException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 注册邀请码管理接口（需要 admin:invite 权限，bootstrap Key 的 * 可放行）。
 */
@RestController
@RequestMapping("/api/admin/invite-codes")
@RequiredArgsConstructor
public class AdminInviteCodeController {

    private final InviteCodeService inviteCodeService;
    private final TenantAccessGuard accessGuard;

    @GetMapping
    public List<InviteCode> list() {
        accessGuard.requireScope("admin:invite");
        return inviteCodeService.list();
    }

    @PostMapping
    public ResponseEntity<?> generate(@RequestBody(required = false) GenerateInviteCodesRequest request) {
        accessGuard.requireScope("admin:invite");
        try {
            int count = request == null || request.count() == null ? 1 : request.count();
            List<String> codes = inviteCodeService.generate(count,
                    request == null ? null : request.quota(),
                    request == null ? null : request.expiresAt(),
                    request == null ? null : request.remark(),
                    currentOperator());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("codes", codes));
        } catch (TenantRegistrationException ex) {
            return ResponseEntity.status(ex.status())
                    .body(Map.of("error", ex.errorCode(), "message", ex.getMessage()));
        }
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<?> disable(@PathVariable String code) {
        accessGuard.requireScope("admin:invite");
        try {
            inviteCodeService.disable(code);
            return ResponseEntity.noContent().build();
        } catch (TenantRegistrationException ex) {
            return ResponseEntity.status(ex.status())
                    .body(Map.of("error", ex.errorCode(), "message", ex.getMessage()));
        }
    }

    private String currentOperator() {
        TenantContext context = TenantContextHolder.getNullable();
        return context == null ? null : context.internalUserId();
    }
}

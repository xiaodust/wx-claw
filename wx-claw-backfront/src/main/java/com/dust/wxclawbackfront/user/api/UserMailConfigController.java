package com.dust.wxclawbackfront.user.api;

import com.dust.wxclawbackfront.tenancy.TenantAccessGuard;
import com.dust.wxclawbackfront.user.api.dto.UserDtos;
import com.dust.wxclawbackfront.user.service.TenantMailConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/mail-config")
@RequiredArgsConstructor
public class UserMailConfigController {

    private final TenantMailConfigService mailConfigService;
    private final TenantAccessGuard accessGuard;

    @GetMapping
    public UserDtos.MailConfig current() {
        accessGuard.requireScope("aiconfig:read");
        return mailConfigService.current();
    }

    @PutMapping
    public UserDtos.MailConfig save(@RequestBody UserDtos.SaveMailConfigRequest request) {
        accessGuard.requireScope("aiconfig:write");
        return mailConfigService.save(request);
    }

    @DeleteMapping
    public void clear() {
        accessGuard.requireScope("aiconfig:write");
        mailConfigService.clear();
    }
}

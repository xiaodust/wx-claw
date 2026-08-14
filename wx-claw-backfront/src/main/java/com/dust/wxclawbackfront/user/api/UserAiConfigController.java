package com.dust.wxclawbackfront.user.api;

import com.dust.wxclawbackfront.tenancy.TenantAccessGuard;
import com.dust.wxclawbackfront.user.api.dto.UserDtos;
import com.dust.wxclawbackfront.user.service.TenantAiConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户自己的 LLM API Key 配置 API。
 */
@RestController
@RequestMapping("/api/user/ai-config")
@RequiredArgsConstructor
public class UserAiConfigController {

    private final TenantAiConfigService configService;
    private final TenantAccessGuard accessGuard;

    @GetMapping
    public UserDtos.AiConfigs current() {
        accessGuard.requireScope("aiconfig:read");
        return configService.current();
    }

    @PutMapping("/{capability}")
    public UserDtos.AiConfigEntry save(@PathVariable String capability,
                                       @RequestBody UserDtos.UpdateAiConfigRequest request) {
        accessGuard.requireScope("aiconfig:write");
        return configService.save(capability, request == null ? null : request.apiKey());
    }

    @DeleteMapping("/{capability}")
    public ResponseEntity<Void> clear(@PathVariable String capability) {
        accessGuard.requireScope("aiconfig:write");
        configService.clear(capability);
        return ResponseEntity.noContent().build();
    }
}

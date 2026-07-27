package com.dust.wxclawbackfront.admin.api;

import com.dust.wxclawbackfront.admin.api.dto.AdminDtos;
import com.dust.wxclawbackfront.admin.service.AdminQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/invocations")
@RequiredArgsConstructor
public class AdminLlmInvocationController {
    private final AdminQueryService queryService;

    @GetMapping("/{invocationId}")
    public ResponseEntity<AdminDtos.InvocationDetail> invocation(@PathVariable String invocationId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(queryService.invocation(invocationId));
    }
}

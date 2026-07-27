package com.dust.wxclawbackfront.admin.api;

import com.dust.wxclawbackfront.admin.api.dto.AdminDtos;
import com.dust.wxclawbackfront.admin.service.AdminQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/overview")
@RequiredArgsConstructor
public class AdminOverviewController {
    private final AdminQueryService queryService;

    @GetMapping
    public AdminDtos.Overview overview(@RequestParam(required = false) String tenantId) {
        return queryService.overview(tenantId);
    }
}

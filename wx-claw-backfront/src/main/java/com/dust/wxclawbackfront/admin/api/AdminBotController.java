package com.dust.wxclawbackfront.admin.api;

import com.dust.wxclawbackfront.admin.api.dto.AdminDtos;
import com.dust.wxclawbackfront.admin.service.AdminQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/bots")
@RequiredArgsConstructor
public class AdminBotController {
    private final AdminQueryService queryService;

    @GetMapping
    public List<AdminDtos.BotStatus> bots(@RequestParam(required = false) String tenantId,
                                         @RequestParam(required = false) String runtimeStatus,
                                         @RequestParam(required = false) String keyword) {
        return queryService.bots(tenantId, runtimeStatus, keyword);
    }

    @GetMapping("/{tenantId}/{botId}")
    public AdminDtos.BotStatus bot(@PathVariable String tenantId, @PathVariable String botId) {
        return queryService.bot(tenantId, botId);
    }
}

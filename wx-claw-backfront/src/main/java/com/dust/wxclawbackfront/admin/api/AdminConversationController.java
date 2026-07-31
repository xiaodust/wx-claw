package com.dust.wxclawbackfront.admin.api;

import com.dust.wxclawbackfront.admin.api.dto.AdminDtos;
import com.dust.wxclawbackfront.admin.service.AdminQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/conversations")
@RequiredArgsConstructor
public class AdminConversationController {
    private final AdminQueryService queryService;

    @GetMapping
    public AdminDtos.PageResult<AdminDtos.Conversation> conversations(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String botId,
            @RequestParam(required = false) String internalUserId,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return AdminDtos.PageResult.from(queryService.conversations(
                tenantId, botId, internalUserId, sessionId, keyword,
                active, startTime, endTime, page, size));
    }

    @GetMapping("/{conversationId}")
    public AdminDtos.Conversation conversation(@PathVariable String conversationId) {
        return queryService.conversation(conversationId);
    }

    @GetMapping("/{conversationId}/messages")
    public List<AdminDtos.Message> messages(@PathVariable String conversationId) {
        return queryService.messages(conversationId);
    }

    @GetMapping("/{conversationId}/invocations")
    public List<AdminDtos.InvocationSummary> invocations(@PathVariable String conversationId) {
        return queryService.invocations(conversationId);
    }
}

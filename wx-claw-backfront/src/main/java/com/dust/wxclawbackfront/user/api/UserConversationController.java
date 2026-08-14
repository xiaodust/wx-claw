package com.dust.wxclawbackfront.user.api;

import com.dust.wxclawbackfront.tenancy.TenantAccessGuard;
import com.dust.wxclawbackfront.user.api.dto.UserDtos;
import com.dust.wxclawbackfront.user.service.UserConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户自助会话/聊天记录 API。
 */
@RestController
@RequestMapping("/api/user/bots/{botId}/conversations")
@RequiredArgsConstructor
public class UserConversationController {

    private final UserConversationService conversationService;
    private final TenantAccessGuard accessGuard;

    @GetMapping
    public List<UserDtos.Conversation> conversations(@PathVariable String botId,
                                                     @RequestParam(defaultValue = "20") int limit) {
        accessGuard.requireScope("conversation:read");
        return conversationService.conversations(botId, limit);
    }

    @GetMapping("/{conversationId}/messages")
    public List<UserDtos.Message> messages(@PathVariable String botId,
                                           @PathVariable String conversationId) {
        accessGuard.requireScope("conversation:read");
        return conversationService.messages(botId, conversationId);
    }
}

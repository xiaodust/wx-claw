package com.dust.wxclawbackfront.user.api;

import com.dust.wxclawbackfront.tenancy.TenantAccessGuard;
import com.dust.wxclawbackfront.user.api.dto.UserDtos;
import com.dust.wxclawbackfront.user.service.UserBotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户自助 Bot 管理 API。
 */
@RestController
@RequestMapping("/api/user/bots")
@RequiredArgsConstructor
public class UserBotController {

    private final UserBotService botService;
    private final TenantAccessGuard accessGuard;

    @GetMapping
    public List<UserDtos.Bot> listBots() {
        accessGuard.requireScope("userbot:read");
        return botService.listBots();
    }

    @PostMapping
    public UserDtos.Bot createBot(@RequestBody(required = false) UserDtos.CreateBotRequest request) {
        accessGuard.requireScope("userbot:write");
        return botService.createBot(request == null ? null : request.displayName());
    }

    @GetMapping("/{botId}")
    public UserDtos.Bot bot(@PathVariable String botId) {
        accessGuard.requireScope("userbot:read");
        return botService.bot(botId);
    }

    @GetMapping("/{botId}/qr")
    public UserDtos.Qr qr(@PathVariable String botId) {
        accessGuard.requireScope("userbot:read");
        return botService.qr(botId);
    }

    @DeleteMapping("/{botId}")
    public ResponseEntity<Void> deleteBot(@PathVariable String botId) {
        accessGuard.requireScope("userbot:write");
        botService.deleteBot(botId);
        return ResponseEntity.noContent().build();
    }
}

package com.dust.wxclawbackfront.user.service;

import com.dust.wxclawbackfront.ilink.ILinkBotService;
import com.dust.wxclawbackfront.ilink.runtime.BotRuntimeKey;
import com.dust.wxclawbackfront.ilink.runtime.status.BotRuntimeSnapshot;
import com.dust.wxclawbackfront.ilink.runtime.status.BotRuntimeStatusRegistry;
import com.dust.wxclawbackfront.ilink.runtime.ILinkRuntimeManager;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.dust.wxclawbackfront.tenancy.entity.TenantBot;
import com.dust.wxclawbackfront.tenancy.repository.TenantBotRepository;
import com.dust.wxclawbackfront.user.api.dto.UserDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 用户自助 Bot 管理：创建/列表/查询/停用，以及扫码二维码的获取。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserBotService {

    private static final String CHANNEL_ILINK = "ILINK";

    private final TenantBotRepository botRepository;
    private final BotRuntimeStatusRegistry statusRegistry;
    private final ILinkBotService ilinkBotService;
    private final ILinkRuntimeManager runtimeManager;

    public List<UserDtos.Bot> listBots() {
        String tenantId = TenantContextHolder.require().tenantId();
        return botRepository.findAllByTenantId(tenantId).stream()
                .filter(bot -> CHANNEL_ILINK.equals(bot.getChannel()))
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public UserDtos.Bot createBot(String displayName) {
        String tenantId = TenantContextHolder.require().tenantId();
        String name = displayName == null || displayName.isBlank()
                ? "我的 Bot" : displayName.trim();
        TenantBot bot = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            String botId = "ub-" + UUID.randomUUID().toString().substring(0, 8).toLowerCase();
            TenantBot candidate = new TenantBot();
            candidate.setChannel(CHANNEL_ILINK);
            candidate.setBotId(botId);
            candidate.setDisplayName(name);
            candidate.setStatus("ACTIVE");
            try {
                bot = botRepository.save(candidate);
                break;
            } catch (DataIntegrityViolationException ex) {
                log.warn("botId 冲突，重试生成: {}", botId);
            }
        }
        if (bot == null) {
            throw new IllegalStateException("创建 Bot 失败：botId 生成冲突");
        }
        ilinkBotService.startBot(new BotRuntimeKey(bot.getTenantId(), bot.getBotId()));
        log.info("用户创建 Bot: tenantId={}, botId={}, displayName={}", tenantId, bot.getBotId(), name);
        return toDto(bot);
    }

    public UserDtos.Bot bot(String botId) {
        return toDto(requireBot(botId));
    }

    public UserDtos.Qr qr(String botId) {
        TenantBot bot = requireBot(botId);
        BotRuntimeKey key = new BotRuntimeKey(bot.getTenantId(), bot.getBotId());
        BotRuntimeSnapshot snapshot = statusRegistry.get(key).orElse(null);
        return new UserDtos.Qr(
                botId,
                snapshot == null ? null : snapshot.qrContent(),
                snapshot == null ? "UNKNOWN" : snapshot.status().name(),
                snapshot == null ? null : snapshot.statusChangedAt());
    }

    @Transactional
    public void deleteBot(String botId) {
        TenantBot bot = requireBot(botId);
        BotRuntimeKey key = new BotRuntimeKey(bot.getTenantId(), bot.getBotId());
        ilinkBotService.stopBot(key);
        runtimeManager.deleteResumeContext(key);
        statusRegistry.remove(key);
        botRepository.delete(bot);
        log.info("用户删除 Bot: tenantId={}, botId={}", bot.getTenantId(), bot.getBotId());
    }

    private TenantBot requireBot(String botId) {
        String tenantId = TenantContextHolder.require().tenantId();
        return botRepository.findByTenantIdAndBotId(tenantId, botId)
                .orElseThrow(() -> new IllegalArgumentException("Bot not found: " + botId));
    }

    private UserDtos.Bot toDto(TenantBot bot) {
        BotRuntimeKey key = new BotRuntimeKey(bot.getTenantId(), bot.getBotId());
        BotRuntimeSnapshot s = statusRegistry.get(key).orElse(null);
        return new UserDtos.Bot(
                bot.getTenantId(),
                bot.getBotId(),
                bot.getDisplayName(),
                bot.getStatus(),
                s == null ? null : s.status().name(),
                s == null ? null : s.connectedAt(),
                s == null ? null : s.statusChangedAt(),
                s == null ? null : s.lastPollAt(),
                s == null ? null : s.lastMessageAt(),
                s == null ? null : s.lastError(),
                s == null ? 0 : s.reconnectAttempts(),
                s != null && s.qrContent() != null);
    }
}

package com.dust.wxclawbackfront.user.service;

import com.dust.wxclawbackfront.ilink.ILinkBotService;
import com.dust.wxclawbackfront.ilink.runtime.BotRuntimeKey;
import com.dust.wxclawbackfront.ilink.runtime.status.BotRuntimeSnapshot;
import com.dust.wxclawbackfront.ilink.runtime.status.BotRuntimeStatus;
import com.dust.wxclawbackfront.ilink.runtime.status.BotRuntimeStatusRegistry;
import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.dust.wxclawbackfront.tenancy.entity.TenantBot;
import com.dust.wxclawbackfront.tenancy.repository.TenantBotRepository;
import com.dust.wxclawbackfront.user.api.dto.UserDtos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserBotServiceTest {

    private TenantBotRepository botRepository;
    private BotRuntimeStatusRegistry statusRegistry;
    private ILinkBotService ilinkBotService;
    private UserBotService service;

    @BeforeEach
    void setUp() {
        botRepository = mock(TenantBotRepository.class);
        statusRegistry = mock(BotRuntimeStatusRegistry.class);
        ilinkBotService = mock(ILinkBotService.class);
        service = new UserBotService(botRepository, statusRegistry, ilinkBotService);
        TenantContextHolder.set(TenantContext.ilink("tenant-a", "bot-a", "user-a", "req"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createBotPersistsAndStartsRuntime() {
        when(botRepository.save(any(TenantBot.class))).thenAnswer(invocation -> {
            TenantBot bot = invocation.getArgument(0);
            bot.setTenantId("tenant-a");
            return bot;
        });

        UserDtos.Bot created = service.createBot(" 我的第一个机器人 ");

        assertThat(created.botId()).startsWith("ub-");
        assertThat(created.displayName()).isEqualTo("我的第一个机器人");
        verify(ilinkBotService).startBot(new BotRuntimeKey("tenant-a", created.botId()));
    }

    @Test
    void createBotDefaultsDisplayName() {
        when(botRepository.save(any(TenantBot.class))).thenAnswer(invocation -> {
            TenantBot bot = invocation.getArgument(0);
            bot.setTenantId("tenant-a");
            return bot;
        });

        UserDtos.Bot created = service.createBot(null);

        assertThat(created.displayName()).isEqualTo("我的 Bot");
    }

    @Test
    void listBotsOnlyIncludesIlinkChannelWithRuntimeStatus() {
        TenantBot bot = bot("bot-1", "ACTIVE");
        when(botRepository.findAllByTenantId("tenant-a")).thenReturn(List.of(bot));
        BotRuntimeSnapshot snapshot = new BotRuntimeSnapshot(
                new BotRuntimeKey("tenant-a", "bot-1"), BotRuntimeStatus.ONLINE, null, null,
                null, null, null, null, 0, false, null);
        when(statusRegistry.get(new BotRuntimeKey("tenant-a", "bot-1"))).thenReturn(Optional.of(snapshot));

        List<UserDtos.Bot> bots = service.listBots();

        assertThat(bots).hasSize(1);
        assertThat(bots.getFirst().runtimeStatus()).isEqualTo("ONLINE");
    }

    @Test
    void qrReturnsImageContentWhenWaiting() {
        TenantBot bot = bot("bot-1", "ACTIVE");
        when(botRepository.findByTenantIdAndBotId("tenant-a", "bot-1")).thenReturn(Optional.of(bot));
        BotRuntimeSnapshot snapshot = new BotRuntimeSnapshot(
                new BotRuntimeKey("tenant-a", "bot-1"), BotRuntimeStatus.WAITING_QR, null, null,
                null, null, null, null, 0, false, "data:image/png;base64,abc");
        when(statusRegistry.get(new BotRuntimeKey("tenant-a", "bot-1"))).thenReturn(Optional.of(snapshot));

        UserDtos.Qr qr = service.qr("bot-1");

        assertThat(qr.status()).isEqualTo("WAITING_QR");
        assertThat(qr.qrImage()).isEqualTo("data:image/png;base64,abc");
    }

    @Test
    void deleteBotDeactivatesAndStopsRuntime() {
        TenantBot bot = bot("bot-1", "ACTIVE");
        when(botRepository.findByTenantIdAndBotId("tenant-a", "bot-1")).thenReturn(Optional.of(bot));

        service.deleteBot("bot-1");

        assertThat(bot.getStatus()).isEqualTo("INACTIVE");
        verify(botRepository).save(bot);
        verify(ilinkBotService).stopBot(new BotRuntimeKey("tenant-a", "bot-1"));
    }

    @Test
    void rejectsBotFromAnotherTenant() {
        when(botRepository.findByTenantIdAndBotId("tenant-a", "bot-1")).thenReturn(Optional.empty());

        try {
            service.bot("bot-1");
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage()).contains("Bot not found");
        }
        verify(ilinkBotService, never()).startBot(any());
    }

    private TenantBot bot(String botId, String status) {
        TenantBot bot = new TenantBot();
        bot.setTenantId("tenant-a");
        bot.setChannel("ILINK");
        bot.setBotId(botId);
        bot.setDisplayName("Bot " + botId);
        bot.setStatus(status);
        return bot;
    }
}

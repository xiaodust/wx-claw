package com.dust.wxclawbackfront.ilink;

import com.dust.wxclawbackfront.ilink.inbound.ILinkMessageDispatcher;
import com.dust.wxclawbackfront.ilink.runtime.ILinkRuntimeManager;
import com.dust.wxclawbackfront.ilink.runtime.BotRuntimeKey;
import com.dust.wxclawbackfront.tenancy.repository.TenantBotRepository;
import com.dust.wxclawbackfront.bot.scheduler.DynamicTaskSchedulerService;
import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.exception.SessionExpiredException;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ILink Bot 服务
 * 只负责编排：启动运行时、轮询消息、委托 dispatcher 处理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ILinkBotService {
    private final Map<BotRuntimeKey, AtomicBoolean> stopFlags = new ConcurrentHashMap<>();

    private final ILinkRuntimeManager runtimeManager;
    private final ILinkMessageDispatcher messageDispatcher;
    private final DynamicTaskSchedulerService taskSchedulerService;
    private final TenantBotRepository tenantBotRepository;
    @Qualifier("messageProcessingExecutor")
    private final ExecutorService messageProcessingExecutor;
    @Qualifier("botRuntimeExecutor")
    private final ExecutorService botRuntimeExecutor;

    @Value("${wxclaw.ilink.poll-idle-ms:200}")
    private long pollIdleMs;

    @Value("${wxclaw.ilink.reconnect.max-attempts:5}")
    private int maxReconnectAttempts;

    @Value("${wxclaw.ilink.reconnect.delay-seconds:30}")
    private int reconnectDelaySeconds;

    /**
     * 运行 ILink 监听服务
     */
    public void startAllActiveBots() {
        List<BotRuntimeKey> keys = tenantBotRepository.findByChannelAndStatus("ILINK", "ACTIVE").stream()
                .map(bot -> new BotRuntimeKey(bot.getTenantId(), bot.getBotId())).toList();
        if (keys.isEmpty()) {
            log.warn("未配置任何 ACTIVE ILink Bot，跳过启动");
            return;
        }
        keys.forEach(key -> botRuntimeExecutor.submit(() -> runILinkMonitor(key)));
        log.info("已启动 {} 个 ILink Bot 运行时", keys.size());
    }

    public void runILinkMonitor(BotRuntimeKey key) {
        AtomicBoolean stopFlag = stopFlags.computeIfAbsent(key, ignored -> new AtomicBoolean(false));
        int reconnectAttempts = 0;

        while (!stopFlag.get()) {
            ILinkClient client = null;
            boolean discardResumeContext = false;
            try {
                client = runtimeManager.createAndLogin(key);

                // 连接就绪后，补偿执行登录前已到期但因未连接而未发送的一次性任务
                try {
                    taskSchedulerService.runOverdueOnceTasks(key);
                } catch (Exception ex) {
                    log.error("补偿执行过期任务失败: {}", ex.getMessage(), ex);
                }

                log.info("开始监听消息...");

                while (!stopFlag.get()) {
                    try {
                        List<WeixinMessage> messages = client.getUpdates();
                        if (reconnectAttempts > 0) {
                            log.info("Bot {} / {} 重连成功，连续失败计数已重置",
                                    key.tenantId(), key.botId());
                            reconnectAttempts = 0;
                        }
                        if (messages != null) {
                            for (WeixinMessage msg : messages) {
                                // 异步处理消息，避免阻塞消息拉取
                                messageProcessingExecutor.submit(() -> {
                                    try {
                                        messageDispatcher.dispatch(key, msg);
                                    } catch (Exception e) {
                                        log.error("消息处理异常: {}", e.getMessage(), e);
                                    }
                                });
                            }
                        }
                    } catch (SessionExpiredException ex) {
                        reconnectAttempts++;
                        discardResumeContext = reconnectAttempts >= maxReconnectAttempts;
                        if (discardResumeContext) {
                            log.warn("Bot {} / {} 登录会话连续重连失败达到上限（{} 次），将清除旧会话并重新扫码登录",
                                    key.tenantId(), key.botId(), maxReconnectAttempts);
                        } else {
                            log.warn("Bot {} / {} 登录会话已过期，尝试恢复连接 {}/{}: {}",
                                    key.tenantId(), key.botId(), reconnectAttempts,
                                    maxReconnectAttempts, ex.getMessage());
                        }
                        break;
                    } catch (Exception ex) {
                        log.warn("监听错误: {}", ex.getMessage());
                        sleepQuietly(1000L);
                    }
                    sleepQuietly(pollIdleMs);
                }

            } catch (Exception ex) {
                if (isQrCodeExpired(ex)) {
                    log.warn("Bot {} / {} 登录二维码已过期，即将自动刷新",
                            key.tenantId(), key.botId());
                    sleepQuietly(1000L);
                } else {
                    log.error("iLink 启动失败: {}", ex.getMessage(), ex);
                    reconnectAttempts++;
                    discardResumeContext = reconnectAttempts >= maxReconnectAttempts;
                    if (discardResumeContext) {
                        log.warn("Bot {} / {} 连续重连失败达到上限（{} 次），将清除旧会话并重新扫码登录",
                                key.tenantId(), key.botId(), maxReconnectAttempts);
                    } else {
                        log.info("等待 {} 秒后重试...", reconnectDelaySeconds);
                        sleepQuietly(reconnectDelaySeconds * 1000L);
                    }
                }
            } finally {
                runtimeManager.closeClient(key, client, !discardResumeContext);
                if (discardResumeContext) {
                    runtimeManager.deleteResumeContext(key);
                    reconnectAttempts = 0;
                }
            }
        }

        stopFlags.remove(key);
    }

    @PreDestroy
    public void stopAllBots() {
        stopFlags.values().forEach(flag -> flag.set(true));
    }

    private void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    static boolean isQrCodeExpired(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("qrcode expired")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

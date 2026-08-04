package com.dust.wxclawbackfront.ilink;

import com.dust.wxclawbackfront.ilink.inbound.ILinkMessageDispatcher;
import com.dust.wxclawbackfront.ilink.runtime.ILinkRuntimeManager;
import com.dust.wxclawbackfront.ilink.runtime.BotRuntimeKey;
import com.dust.wxclawbackfront.ilink.runtime.status.BotRuntimeStatusRegistry;
import com.dust.wxclawbackfront.tenancy.repository.TenantBotRepository;
import com.dust.wxclawbackfront.bot.scheduler.DynamicTaskSchedulerService;
import com.dust.wxclawbackfront.config.KeyedPartitionExecutor;
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
import java.util.concurrent.atomic.AtomicInteger;
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
    private final Map<BotRuntimeKey, AtomicInteger> inFlightMessages = new ConcurrentHashMap<>();

    private final ILinkRuntimeManager runtimeManager;
    private final ILinkMessageDispatcher messageDispatcher;
    private final DynamicTaskSchedulerService taskSchedulerService;
    private final TenantBotRepository tenantBotRepository;
    private final BotRuntimeStatusRegistry statusRegistry;
    @Qualifier("messagePartitionExecutor")
    private final KeyedPartitionExecutor messagePartitionExecutor;
    @Qualifier("botRuntimeExecutor")
    private final ExecutorService botRuntimeExecutor;

    @Value("${wxclaw.ilink.poll-idle-ms:200}")
    private long pollIdleMs;

    @Value("${wxclaw.ilink.reconnect.max-attempts:5}")
    private int maxReconnectAttempts;

    @Value("${wxclaw.ilink.reconnect.delay-seconds:30}")
    private int reconnectDelaySeconds;

    @Value("${wxclaw.ilink.checkpoint.drain-timeout-seconds:300}")
    private long checkpointDrainTimeoutSeconds;

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
        keys.forEach(key -> {
            statusRegistry.starting(key, false);
            botRuntimeExecutor.submit(() -> runILinkMonitor(key));
        });
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
                        statusRegistry.pollSucceeded(key, messages != null && !messages.isEmpty());
                        if (reconnectAttempts > 0) {
                            log.info("Bot {} / {} 重连成功，连续失败计数已重置",
                                    key.tenantId(), key.botId());
                            reconnectAttempts = 0;
                        }
                        if (messages != null) {
                            boolean enqueuedAny = false;
                            for (WeixinMessage msg : messages) {
                                if (!messageDispatcher.claim(key, msg)) {
                                    continue;
                                }
                                inFlightMessages.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
                                enqueuedAny = true;
                                // 按用户分区异步处理消息：同一用户串行保序，不同用户并行，避免阻塞消息拉取
                                messagePartitionExecutor.execute(
                                        new UserMessageKey(key.tenantId(), key.botId(), msg.getFrom_user_id()),
                                        () -> {
                                            try {
                                                messageDispatcher.dispatchClaimed(key, msg);
                                            } catch (Exception e) {
                                                log.error("消息处理异常: {}", e.getMessage(), e);
                                            } finally {
                                                AtomicInteger counter = inFlightMessages.get(key);
                                                if (counter != null) {
                                                    counter.decrementAndGet();
                                                }
                                            }
                                        });
                            }
                            // 游标必须等本批消息全部处理完成后再推进；
                            // 否则异步处理期间崩溃会导致已 claim 但未处理的消息在重启后丢失。
                            if (enqueuedAny && awaitBatchDrain(key, stopFlag)) {
                                runtimeManager.checkpointResumeContext(key, client);
                            } else if (enqueuedAny) {
                                log.warn("批量消息处理未在 {}s 内完成，本次不推进游标，未完成消息将在重新投递时按租约恢复",
                                        checkpointDrainTimeoutSeconds);
                            }
                        }
                    } catch (SessionExpiredException ex) {
                        reconnectAttempts++;
                        statusRegistry.reconnecting(key, reconnectAttempts, ex);
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
                    statusRegistry.waitingForQr(key);
                    sleepQuietly(1000L);
                } else {
                    log.error("iLink 启动失败: {}", ex.getMessage(), ex);
                    reconnectAttempts++;
                    statusRegistry.error(key, reconnectAttempts, ex);
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
                    statusRegistry.waitingForQr(key);
                    reconnectAttempts = 0;
                }
            }
        }

        statusRegistry.stopped(key);
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

    /**
     * 等待本批异步处理全部结束。返回 false 表示超时或收到停止信号。
     */
    private boolean awaitBatchDrain(BotRuntimeKey key, AtomicBoolean stopFlag) {
        AtomicInteger counter = inFlightMessages.get(key);
        if (counter == null) {
            return true;
        }
        long deadline = System.currentTimeMillis() + Math.max(1, checkpointDrainTimeoutSeconds) * 1000L;
        while (counter.get() > 0 && !stopFlag.get()) {
            if (System.currentTimeMillis() >= deadline) {
                return false;
            }
            sleepQuietly(50L);
        }
        if (stopFlag.get()) {
            return false;
        }
        inFlightMessages.remove(key);
        return true;
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

package com.dust.wxclawbackfront.ilink;

import com.dust.wxclawbackfront.ilink.inbound.ILinkMessageDispatcher;
import com.dust.wxclawbackfront.ilink.runtime.ILinkRuntimeManager;
import com.dust.wxclawbackfront.scheduler.DynamicTaskSchedulerService;
import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.exception.SessionExpiredException;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ILink Bot 服务
 * 只负责编排：启动运行时、轮询消息、委托 dispatcher 处理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ILinkBotService {

    private final ILinkRuntimeManager runtimeManager;
    private final ILinkMessageDispatcher messageDispatcher;
    private final DynamicTaskSchedulerService taskSchedulerService;

    @Value("${wxclaw.ilink.poll-idle-ms:200}")
    private long pollIdleMs;

    /**
     * 运行 ILink 监听服务
     */
    public void runILinkMonitor() {
        AtomicBoolean stopFlag = new AtomicBoolean(false);
        ILinkClient client = null;

        try {
            client = runtimeManager.createAndLogin();
            runtimeManager.registerShutdownHook(client, stopFlag);

            // 连接就绪后，补偿执行登录前已到期但因未连接而未发送的一次性任务
            try {
                taskSchedulerService.runOverdueOnceTasks();
            } catch (Exception ex) {
                log.error("补偿执行过期任务失败: {}", ex.getMessage(), ex);
            }

            log.info("开始监听消息...");

            while (!stopFlag.get()) {
                try {
                    List<WeixinMessage> messages = client.getUpdates();
                    if (messages != null) {
                        for (WeixinMessage msg : messages) {
                            messageDispatcher.dispatch(msg);
                        }
                    }
                } catch (SessionExpiredException ex) {
                    log.error("登录会话已过期，需要重新扫码登录: {}", ex.getMessage());
                    // 删除旧的恢复上下文文件，避免下次启动时继续尝试用过期凭证恢复
                    runtimeManager.deleteResumeContext();
                    // 跳出循环，触发外层重新登录（如果有重启机制的话）
                    break;
                } catch (Exception ex) {
                    log.warn("监听错误: {}", ex.getMessage());
                    sleepQuietly(1000L);
                }
                sleepQuietly(pollIdleMs);
            }

        } catch (Exception ex) {
            log.error("iLink 启动失败: {}", ex.getMessage(), ex);
        } finally {
            runtimeManager.closeClient(client);
        }
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
}

package com.dust.wxclawbackfront.ilink.inbound;

import com.dust.wxclawbackfront.ilink.outbound.ILinkMessageSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 等待提示服务
 * 在用户等待处理时发送提示消息
 */
@Slf4j
@Component
public class WaitNoticeService {

    private static final ScheduledExecutorService EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "ai-wait-notice");
                    thread.setDaemon(true);
                    return thread;
                }
            });

    private final ILinkMessageSender messageSender;

    @Value("${wxclaw.ai.wait-notice.enabled:true}")
    private boolean waitNoticeEnabled;

    @Value("${wxclaw.ai.wait-notice.delay-seconds:5}")
    private int waitNoticeDelaySeconds;

    @Value("${wxclaw.ai.wait-notice.text:我正在处理中，可能还需要几秒，请稍等一下。}")
    private String waitNoticeText;

    public WaitNoticeService(ILinkMessageSender messageSender) {
        this.messageSender = messageSender;
    }

    /**
     * 调度等待提示
     * @return ScheduledFuture，可用于取消
     */
    public ScheduledFuture<?> schedule(String userId) {
        if (!waitNoticeEnabled || userId == null || userId.isBlank()) {
            return null;
        }

        int delay = waitNoticeDelaySeconds <= 0 ? 5 : waitNoticeDelaySeconds;
        String text = (waitNoticeText == null || waitNoticeText.isBlank())
                ? "我正在处理中，可能还需要几秒，请稍等一下。"
                : waitNoticeText.trim();

        return EXECUTOR.schedule(() -> {
            try {
                messageSender.sendText(userId, text);
            } catch (Exception ex) {
                log.debug("发送等待提示失败: userId={}, error={}", userId, ex.getMessage());
            }
        }, delay, TimeUnit.SECONDS);
    }

    /**
     * 取消等待提示
     */
    public void cancel(ScheduledFuture<?> future) {
        if (future != null) {
            future.cancel(false);
        }
    }
}

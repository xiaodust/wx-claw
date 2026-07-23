package com.dust.wxclawbackfront.ilink.inbound;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息防抖组件
 * 短时间内相同用户的相同消息只处理一次
 */
@Slf4j
@Component
public class MessageDebouncer {

    private final ConcurrentHashMap<String, Instant> recentMessageCache = new ConcurrentHashMap<>();
    private static final Duration DEBOUNCE_DURATION = Duration.ofSeconds(3);
    private static final Duration CLEANUP_THRESHOLD = DEBOUNCE_DURATION.multipliedBy(10);

    /**
     * 检查消息是否应该被处理
     * @return true 如果消息应该被处理，false 如果应该被跳过
     */
    public boolean shouldProcess(String userId, String userText) {
        if (userText == null || userText.isBlank()) {
            return true;
        }

        String messageKey = userId + "::" + userText.trim().hashCode();
        Instant now = Instant.now();
        Instant lastProcessed = recentMessageCache.get(messageKey);

        if (lastProcessed != null && Duration.between(lastProcessed, now).compareTo(DEBOUNCE_DURATION) < 0) {
            log.debug("消息防抖：跳过重复消息 userId={}, text={}", userId,
                    userText.trim().length() > 20 ? userText.trim().substring(0, 20) + "..." : userText.trim());
            return false;
        }

        recentMessageCache.put(messageKey, now);
        cleanExpiredCache(now);
        return true;
    }

    /**
     * 清理过期的防抖缓存
     */
    private void cleanExpiredCache(Instant now) {
        if (recentMessageCache.size() > 100) {
            recentMessageCache.entrySet().removeIf(entry ->
                    Duration.between(entry.getValue(), now).compareTo(CLEANUP_THRESHOLD) > 0
            );
        }
    }
}

package com.dust.wxclawbackfront.ilink.inbound;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
     * 检查消息是否应该被处理。
     *
     * <p>去重维度为 租户 + Bot + 用户 + 内容哈希：不同租户或不同 Bot 下的用户
     * （微信 openId 可能相同）互不影响，与消息分区键 {@code UserMessageKey} 维度一致。
     *
     * @return true 如果消息应该被处理，false 如果应该被跳过
     */
    public boolean shouldProcess(String tenantId, String botId, String userId, String userText) {
        if (userText == null || userText.isBlank()) {
            return true;
        }

        // 使用SHA-256哈希而不是简单的hashCode，避免哈希冲突
        String textHash = sha256Hash(userText.trim());
        String messageKey = tenantId + "::" + botId + "::" + userId + "::" + textHash;
        Instant now = Instant.now();
        Instant lastProcessed = recentMessageCache.get(messageKey);

        if (lastProcessed != null && Duration.between(lastProcessed, now).compareTo(DEBOUNCE_DURATION) < 0) {
            log.debug("消息防抖：跳过重复消息 tenantId={}, botId={}, userId={}, text={}", tenantId, botId, userId,
                    userText.trim().length() > 20 ? userText.trim().substring(0, 20) + "..." : userText.trim());
            return false;
        }

        recentMessageCache.put(messageKey, now);
        cleanExpiredCache(now);
        return true;
    }

    /**
     * 使用SHA-256计算文本哈希值
     */
    private String sha256Hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes());
            BigInteger number = new BigInteger(1, hashBytes);
            StringBuilder hexString = new StringBuilder(number.toString(16));
            
            // 确保哈希值是64位十六进制字符串（补零）
            while (hexString.length() < 64) {
                hexString.insert(0, '0');
            }
            
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // 如果SHA-256不可用，回退到hashCode，但这种情况几乎不会发生
            log.warn("SHA-256不可用，使用hashCode作为备选方案", e);
            return String.valueOf(input.hashCode());
        }
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

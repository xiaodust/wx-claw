package com.dust.wxclawbackfront.ilink;

/**
 * 消息处理的分区键：租户 + Bot + 用户 三个维度共同决定消息归属分区。
 *
 * <p>同一用户的连续消息会进入同一分区串行处理，保证回复不乱序；
 * 不同用户（或不同 Bot 下的用户）哈希到不同分区，可并行处理。
 */
public record UserMessageKey(String tenantId, String botId, String userId) {
}

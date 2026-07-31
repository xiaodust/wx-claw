package com.dust.wxclawbackfront.tenancy;

import java.util.Collections;
import java.util.Set;

/**
 * 一次请求或消息处理过程中的租户身份快照。
 *
 * <p>该对象同时描述租户、接入渠道、Bot、用户和权限信息，是数据库隔离、
 * API 授权、异步任务传播以及审计记录的共同数据来源。record 保持不可变，
 * 避免上下文在线程间传递后被意外修改。</p>
 */
public record TenantContext(
        String tenantId,
        String channel,
        String botId,
        String internalUserId,
        String channelUserId,
        Set<String> roles,
        Set<String> scopes,
        String requestId
) {
    /** 将外部集合复制为不可变集合，调用方后续修改原集合不会影响当前上下文。 */
    public TenantContext {
        roles = roles == null ? Collections.emptySet() : Set.copyOf(roles);
        scopes = scopes == null ? Collections.emptySet() : Set.copyOf(scopes);
    }

    /** 为微信 iLink 入站消息创建上下文；当前渠道用户 ID 同时作为内部用户 ID。 */
    public static TenantContext ilink(String tenantId, String botId, String channelUserId, String requestId) {
        return new TenantContext(tenantId, "ILINK", botId, channelUserId, channelUserId,
                Collections.emptySet(), Collections.emptySet(), requestId);
    }
}

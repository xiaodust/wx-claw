package com.dust.wxclawbackfront.bot.agent.tools.shared;

import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;

import java.util.Collections;
import java.util.UUID;

/**
 * 用户上下文持有器，用于在 AI Tool 调用过程中传递当前用户的 userId
 * 使用 ThreadLocal 确保线程安全
 */
public class UserContextHolder {

    /**
     * 设置当前用户ID
     */
    public static void setUserId(String userId) {
        TenantContext current = TenantContextHolder.getNullable();
        if (current != null) {
            TenantContextHolder.set(new TenantContext(current.tenantId(), current.channel(), current.botId(), userId,
                    current.channelUserId(), current.roles(), current.scopes(), current.requestId()));
            return;
        }
        TenantContextHolder.set(new TenantContext("default", "INTERNAL", "default", userId, userId,
                Collections.emptySet(), Collections.emptySet(), UUID.randomUUID().toString()));
    }

    /**
     * 获取当前用户ID
     */
    public static String getUserId() {
        TenantContext context = TenantContextHolder.getNullable();
        return context == null ? null : context.internalUserId();
    }

    /**
     * 清除当前用户ID
     */
    public static void clear() {
        TenantContextHolder.clear();
    }

    /**
     * 检查是否已设置用户ID
     */
    public static boolean hasUserId() {
        return getUserId() != null;
    }
}

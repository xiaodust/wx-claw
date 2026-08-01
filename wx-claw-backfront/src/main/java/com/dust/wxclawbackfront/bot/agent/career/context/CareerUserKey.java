package com.dust.wxclawbackfront.bot.agent.career.context;

import com.dust.wxclawbackfront.bot.agent.mcp.jobhelper.JobHelperMcpClient.UserIdentity;
import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;

public record CareerUserKey(String tenantId, String botId, String userId) {
    public String externalUserId() {
        return "wx-claw/" + botId + "/" + userId;
    }

    public UserIdentity jobHelperIdentity() {
        return new UserIdentity(tenantId, externalUserId());
    }

    public static CareerUserKey current() {
        return from(TenantContextHolder.require());
    }

    public static CareerUserKey from(TenantContext context) {
        String userId = firstNonBlank(context.internalUserId(), context.channelUserId());
        if (context.botId() == null || context.botId().isBlank() || userId == null) {
            throw new IllegalStateException("Bot ID and user ID are required for career context");
        }
        return new CareerUserKey(context.tenantId(), context.botId(), userId);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null || second.isBlank() ? null : second;
    }
}

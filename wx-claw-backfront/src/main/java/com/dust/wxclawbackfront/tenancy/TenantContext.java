package com.dust.wxclawbackfront.tenancy;

import java.util.Collections;
import java.util.Set;

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
    public TenantContext {
        roles = roles == null ? Collections.emptySet() : Set.copyOf(roles);
        scopes = scopes == null ? Collections.emptySet() : Set.copyOf(scopes);
    }

    public static TenantContext ilink(String tenantId, String botId, String channelUserId, String requestId) {
        return new TenantContext(tenantId, "ILINK", botId, channelUserId, channelUserId,
                Collections.emptySet(), Collections.emptySet(), requestId);
    }
}

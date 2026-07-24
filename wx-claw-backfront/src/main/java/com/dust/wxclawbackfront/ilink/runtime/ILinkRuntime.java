package com.dust.wxclawbackfront.ilink.runtime;

import com.github.wechat.ilink.sdk.ILinkClient;

import java.time.Instant;

public record ILinkRuntime(BotRuntimeKey key, ILinkClient client, Instant connectedAt) {
}

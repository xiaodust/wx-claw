package com.dust.wxclawbackfront.bot.api.io;

import lombok.Data;

@Data
public class AiConversationCreateRequest {
    private String sessionId;
    private String username;
}

package com.dust.wxclawbackfront.ai.api.io;

import lombok.Data;

@Data
public class AiConversationCreateRequest {
    private String sessionId;
    private String username;
}

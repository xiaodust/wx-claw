package com.dust.wxclawbackfront.ai.trace;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class AiChatTrace {
    private OffsetDateTime timestamp;
    private String sessionId;
    private String contextToken;
    private String ilinkMessageJson;
    private String model;
    private String llmRequestJson;
    private String messageItemType;
    private String imageUrl;
    private String imageModel;
    private String imageDescription;
    private String imageLlmRequestJson;
    private String imageLocalPath;
    private String userText;
    private String requestText;
    private String replyText;
    private Integer responseTimeMs;
    private String errorMsg;
}

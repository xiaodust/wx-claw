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
    private String toolName;
    private String toolRequest;
    private String toolResponse;
    private String messageItemType;
    private String imageUrl;
    private String imageModel;
    private String imageDescription;
    private String imageLlmRequestJson;
    private String generatedImageUrl;
    private String generatedImageRequestJson;
    private String generatedImageResponseJson;
    private String ttsRequestJson;
    private String ttsResponseJson;
    private Integer ttsPlayTimeMs;
    private Integer ttsSampleRate;
    private String userText;
    private String requestText;
    private String replyText;
    private Integer responseTimeMs;
    private String errorMsg;
}

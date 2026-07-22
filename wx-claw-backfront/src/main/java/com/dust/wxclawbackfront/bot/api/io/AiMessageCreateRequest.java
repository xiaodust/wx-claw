package com.dust.wxclawbackfront.bot.api.io;

import lombok.Data;

@Data
public class AiMessageCreateRequest {
    private Integer messageType;
    private String content;
    private String reasoningContent;
    private Integer responseTime;
    private String errorMsg;
}

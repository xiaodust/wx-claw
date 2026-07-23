package com.dust.wxclawbackfront.bot.api.io;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiMessageDTO {
    private String id;
    private String sessionId;
    private Integer messageType;
    private String content;
    private String reasoningContent;
    private Integer messageSeq;
    private Integer responseTime;
    private String errorMsg;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

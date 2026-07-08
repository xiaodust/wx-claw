package com.dust.wxclawbackfront.ai.api.io;

import lombok.Data;

import java.util.Date;

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
    private Date createTime;
    private Date updateTime;
}

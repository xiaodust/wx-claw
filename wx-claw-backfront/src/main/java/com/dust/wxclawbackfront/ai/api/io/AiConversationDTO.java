package com.dust.wxclawbackfront.ai.api.io;

import lombok.Data;

import java.util.Date;

@Data
public class AiConversationDTO {
    private String id;
    private String sessionId;
    private String username;
    private Boolean active;
    private Integer messageCount;
    private Date lastMessageTime;
    private Date createdTime;
    private Date updatedTime;
}

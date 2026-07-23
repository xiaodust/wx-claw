package com.dust.wxclawbackfront.bot.api.io;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiConversationDTO {
    private String id;
    private String sessionId;
    private String username;
    private Boolean active;
    private Integer messageCount;
    private LocalDateTime lastMessageTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}

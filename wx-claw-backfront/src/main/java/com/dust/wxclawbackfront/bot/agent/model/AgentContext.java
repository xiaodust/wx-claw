package com.dust.wxclawbackfront.bot.agent.model;

import com.dust.wxclawbackfront.bot.dao.entity.AiMessage;
import com.dust.wxclawbackfront.bot.dao.entity.UserProfile;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Agent 对话上下文
 */
@Data
@Builder
public class AgentContext {

    private String userId;
    private List<AiMessage> historyMessages;
    private List<UserProfile> userProfiles;
    private String userText;
}

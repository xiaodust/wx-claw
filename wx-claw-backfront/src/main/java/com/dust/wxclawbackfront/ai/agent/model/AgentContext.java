package com.dust.wxclawbackfront.ai.agent.model;

import com.dust.wxclawbackfront.ai.dao.entity.AiMessage;
import com.dust.wxclawbackfront.ai.dao.entity.UserProfile;
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

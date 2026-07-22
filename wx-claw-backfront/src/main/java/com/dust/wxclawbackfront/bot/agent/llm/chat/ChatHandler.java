package com.dust.wxclawbackfront.bot.agent.llm.chat;

import com.dust.wxclawbackfront.bot.dao.entity.AiMessage;

import java.util.List;

/**
 * 聊天处理器接口
 */
public interface ChatHandler {
    String chat(String userMessage, List<AiMessage> historyMessages);
}

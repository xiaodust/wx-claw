package com.dust.wxclawbackfront.ai.chat;

import com.dust.wxclawbackfront.ai.dao.entity.AiMessage;

import java.util.List;

/**
 * 聊天处理器接口
 */
public interface ChatHandler {
    String chat(String userMessage, List<AiMessage> historyMessages, AIContentAccumulator accumulator);
}

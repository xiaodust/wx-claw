package com.dust.wxclawbackfront.ai.service.chat;

import com.dust.wxclawbackfront.ai.dao.entity.AiMessage;

import java.util.List;

public interface ChatHandler {
 String chat(String userMessage, List<AiMessage> historyMessages,
                     AIContentAccumulator accumulator);
}


package com.dust.wxclawbackfront.ai.service;

import com.dust.wxclawbackfront.ai.dao.entity.AiMessage;
import com.dust.wxclawbackfront.ai.tools.chat.AIContentAccumulator;

import java.util.List;

public interface ChatHandler {
 String chat(String userMessage, List<AiMessage> historyMessages,
                     AIContentAccumulator accumulator);
}


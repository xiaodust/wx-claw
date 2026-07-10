package com.dust.wxclawbackfront.ai.tools.reminder.executor;

import com.dust.wxclawbackfront.ai.chat.PlainTextLlmService;
import com.dust.wxclawbackfront.ai.tools.reminder.ReminderTask;
import com.dust.wxclawbackfront.ilnk.outbound.ILinkMessageSender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


/**
 * AI 自动聊天执行器
 * 定时让 AI 生成内容并发送
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiChatExecutor implements TaskActionExecutor {
    
    private final PlainTextLlmService plainTextLlmService;
    private final ILinkMessageSender messageSender;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public boolean execute(ReminderTask task) {
        try {
            String actionParams = task.getActionParams();
            if (actionParams == null || actionParams.isBlank()) {
                log.error("AI聊天任务参数为空: taskId={}", task.getId());
                return false;
            }
            
            // 解析参数
            JsonNode params = objectMapper.readTree(actionParams);
            String prompt = params.get("prompt").asText();
            
            log.info("执行AI聊天任务: taskId={}, userId={}, prompt={}", 
                    task.getId(), task.getUserId(), prompt);
            
            // 调用纯文本 LLM 生成内容
            String aiResponse = plainTextLlmService.chat(prompt);
            
            if (aiResponse == null || aiResponse.isBlank()) {
                log.error("AI生成内容为空: taskId={}", task.getId());
                return false;
            }
            
            // 发送消息
            messageSender.sendText(task.getUserId(), aiResponse);
            
            log.info("AI聊天任务完成: taskId={}, userId={}", task.getId(), task.getUserId());
            return true;
            
        } catch (Exception e) {
            log.error("AI聊天任务执行异常: taskId={}, error={}", task.getId(), e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public String getActionType() {
        return "AI_CHAT";
    }
}

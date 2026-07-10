package com.dust.wxclawbackfront.ai.tools.reminder.executor;

import com.dust.wxclawbackfront.ai.tools.mail.MailHandler;
import com.dust.wxclawbackfront.ai.tools.mail.MailSendResult;
import com.dust.wxclawbackfront.ai.tools.reminder.ReminderTask;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * 邮件动作执行器
 * 定时发送邮件
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(MailHandler.class)
public class EmailActionExecutor implements TaskActionExecutor {
    
    private final MailHandler mailHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public boolean execute(ReminderTask task) {
        try {
            String actionParams = task.getActionParams();
            if (actionParams == null || actionParams.isBlank()) {
                log.error("邮件任务参数为空: taskId={}", task.getId());
                return false;
            }
            
            // 解析参数
            JsonNode params = objectMapper.readTree(actionParams);
            String to = params.get("to").asText();
            String subject = params.get("subject").asText();
            String content = params.get("content").asText();
            boolean isHtml = params.has("isHtml") && params.get("isHtml").asBoolean();
            
            log.info("执行邮件发送任务: taskId={}, to={}, subject={}", task.getId(), to, subject);
            
            MailSendResult result = mailHandler.send(to, subject, content, isHtml);
            
            if (result.isSuccess()) {
                log.info("邮件发送成功: taskId={}, to={}", task.getId(), to);
                return true;
            } else {
                log.error("邮件发送失败: taskId={}, error={}", task.getId(), result.getErrorMsg());
                return false;
            }
            
        } catch (Exception e) {
            log.error("邮件任务执行异常: taskId={}, error={}", task.getId(), e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public String getActionType() {
        return "EMAIL";
    }
}

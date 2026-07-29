package com.dust.wxclawbackfront.bot.agent.tools.reminder.executor;

import com.dust.wxclawbackfront.bot.agent.tools.mail.MailHandler;
import com.dust.wxclawbackfront.bot.agent.tools.mail.MailSendResult;
import com.dust.wxclawbackfront.bot.dao.entity.ReminderTask;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(MailHandler.class)
public class WeatherEmailExecutor implements TaskActionExecutor {

    private final WeatherReportService weatherReportService;
    private final MailHandler mailHandler;
    private final ObjectMapper objectMapper;

    @Override
    public boolean execute(ReminderTask task) {
        try {
            JsonNode params = objectMapper.readTree(task.getActionParams());
            String to = params.path("to").asText();
            String subject = params.path("subject").asText();
            String location = params.path("location").asText();
            boolean includeForecast = params.path("includeForecast").asBoolean();

            WeatherReportService.WeatherReport weather = weatherReportService.build(location, includeForecast);
            if (!weather.success()) {
                log.error("天气邮件查询失败: taskId={}, location={}, error={}",
                        task.getId(), location, weather.errorMessage());
                return false;
            }
            MailSendResult result = mailHandler.send(to, subject, weather.content(), false);
            if (!result.isSuccess()) {
                log.error("天气邮件发送失败: taskId={}, to={}, error={}", task.getId(), to, result.getErrorMsg());
                return false;
            }
            log.info("天气邮件发送成功: taskId={}, to={}, location={}", task.getId(), to, location);
            return true;
        } catch (Exception e) {
            log.error("天气邮件任务执行异常: taskId={}, error={}", task.getId(), e.getMessage(), e);
            return false;
        }
    }

    @Override
    public String getActionType() {
        return "WEATHER_EMAIL";
    }
}

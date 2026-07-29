package com.dust.wxclawbackfront.bot.agent.tools.reminder.executor;

import com.dust.wxclawbackfront.bot.agent.tools.mail.MailHandler;
import com.dust.wxclawbackfront.bot.agent.tools.mail.MailSendResult;
import com.dust.wxclawbackfront.bot.agent.tools.search.BochaWebSearchHandler;
import com.dust.wxclawbackfront.bot.agent.tools.search.BochaWebSearchResult;
import com.dust.wxclawbackfront.bot.dao.entity.ReminderTask;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(MailHandler.class)
public class ScheduledBriefingEmailExecutor implements TaskActionExecutor {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy年M月d日");

    private final BochaWebSearchHandler searchHandler;
    private final WeatherReportService weatherReportService;
    private final MailHandler mailHandler;
    private final ObjectMapper objectMapper;

    @Value("${wxclaw.ai.time.zone:Asia/Shanghai}")
    private String timeZone = "Asia/Shanghai";

    @Override
    public boolean execute(ReminderTask task) {
        try {
            JsonNode params = objectMapper.readTree(task.getActionParams());
            String to = params.path("to").asText();
            String subject = params.path("subject").asText("资讯简报");
            String location = params.path("location").asText();
            String newsQuery = params.path("newsQuery").asText();
            int newsCount = params.path("newsCount").asInt(5);
            boolean includeForecast = params.path("includeForecast").asBoolean();

            BochaWebSearchResult news = searchHandler.search(newsQuery, "oneDay", newsCount);
            if (news == null || news.getErrorMsg() != null || news.getItems() == null || news.getItems().isEmpty()) {
                log.error("简报新闻查询失败: taskId={}, query={}, error={}", task.getId(), newsQuery,
                        news == null ? "返回为空" : news.getErrorMsg());
                return false;
            }

            WeatherReportService.WeatherReport weather = weatherReportService.build(location, includeForecast);
            if (!weather.success()) {
                log.error("简报天气查询失败: taskId={}, location={}, error={}",
                        task.getId(), location, weather.errorMessage());
                return false;
            }

            StringBuilder content = new StringBuilder();
            content.append(LocalDate.now(ZoneId.of(timeZone)).format(DATE_FORMAT)).append(" 资讯简报\n\n")
                    .append("【天气】\n").append(weather.content());
            content.append("\n\n【今日资讯】\n").append(searchHandler.formatReply(news));
            MailSendResult result = mailHandler.send(to, subject, content.toString(), false);
            if (!result.isSuccess()) {
                log.error("简报邮件发送失败: taskId={}, to={}, error={}", task.getId(), to, result.getErrorMsg());
                return false;
            }
            log.info("简报邮件发送成功: taskId={}, to={}, location={}, query={}",
                    task.getId(), to, location, newsQuery);
            return true;
        } catch (Exception e) {
            log.error("简报邮件任务执行异常: taskId={}, error={}", task.getId(), e.getMessage(), e);
            return false;
        }
    }

    @Override
    public String getActionType() {
        return "SCHEDULED_BRIEFING_EMAIL";
    }
}

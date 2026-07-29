package com.dust.wxclawbackfront.bot.agent.tools.reminder.executor;

import com.dust.wxclawbackfront.bot.agent.tools.mail.MailHandler;
import com.dust.wxclawbackfront.bot.agent.tools.mail.MailSendResult;
import com.dust.wxclawbackfront.bot.agent.tools.weather.SeniverseWeatherHandler;
import com.dust.wxclawbackfront.bot.agent.tools.weather.WeatherNowResult;
import com.dust.wxclawbackfront.bot.dao.entity.ReminderTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeatherEmailExecutorTest {

    private final SeniverseWeatherHandler weatherHandler = mock(SeniverseWeatherHandler.class);
    private final WeatherReportService weatherReportService = new WeatherReportService(weatherHandler);
    private final MailHandler mailHandler = mock(MailHandler.class);
    private final WeatherEmailExecutor executor = new WeatherEmailExecutor(
            weatherReportService, mailHandler, new ObjectMapper());

    @Test
    void queriesWeatherAtExecutionTimeAndSendsFormattedResult() {
        ReminderTask task = task("{\"to\":\"user@example.com\",\"subject\":\"今日天气\","
                + "\"location\":\"杭州\",\"includeForecast\":false}");
        WeatherNowResult now = successfulNow();
        when(weatherHandler.now("杭州")).thenReturn(now);
        when(weatherHandler.formatReply(now)).thenReturn("杭州当前晴，气温30°C");
        when(mailHandler.send("user@example.com", "今日天气", "杭州当前晴，气温30°C", false))
                .thenReturn(MailSendResult.success("user@example.com", "今日天气", "now"));

        assertThat(executor.execute(task)).isTrue();

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(mailHandler).send(org.mockito.ArgumentMatchers.eq("user@example.com"),
                org.mockito.ArgumentMatchers.eq("今日天气"), content.capture(),
                org.mockito.ArgumentMatchers.eq(false));
        assertThat(content.getValue()).contains("杭州当前晴").doesNotContain("这是今天的天气");
    }

    @Test
    void doesNotSendPlaceholderMailWhenWeatherQueryFails() {
        ReminderTask task = task("{\"to\":\"user@example.com\",\"subject\":\"今日天气\","
                + "\"location\":\"杭州\",\"includeForecast\":false}");
        WeatherNowResult failed = new WeatherNowResult(null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, "API unavailable");
        when(weatherHandler.now("杭州")).thenReturn(failed);

        assertThat(executor.execute(task)).isFalse();
        verify(mailHandler, never()).send(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    private ReminderTask task(String params) {
        ReminderTask task = new ReminderTask();
        task.setId(34L);
        task.setActionParams(params);
        return task;
    }

    private WeatherNowResult successfulNow() {
        return new WeatherNowResult(null, null, "id", "杭州", "中国,浙江,杭州", "Asia/Shanghai",
                "2026-07-29T10:00:00+08:00", "晴", "0", "30", "32", "60", "东", "2", "8", null);
    }
}

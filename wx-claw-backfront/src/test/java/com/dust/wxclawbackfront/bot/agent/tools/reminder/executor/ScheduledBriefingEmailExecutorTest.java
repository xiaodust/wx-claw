package com.dust.wxclawbackfront.bot.agent.tools.reminder.executor;

import com.dust.wxclawbackfront.bot.agent.tools.mail.MailHandler;
import com.dust.wxclawbackfront.bot.agent.tools.mail.MailSendResult;
import com.dust.wxclawbackfront.bot.agent.tools.search.BochaWebSearchHandler;
import com.dust.wxclawbackfront.bot.agent.tools.search.BochaWebSearchResult;
import com.dust.wxclawbackfront.bot.dao.entity.ReminderTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduledBriefingEmailExecutorTest {

    private final BochaWebSearchHandler searchHandler = mock(BochaWebSearchHandler.class);
    private final WeatherReportService weatherReportService = mock(WeatherReportService.class);
    private final MailHandler mailHandler = mock(MailHandler.class);
    private final ScheduledBriefingEmailExecutor executor = new ScheduledBriefingEmailExecutor(
            searchHandler, weatherReportService, mailHandler, new ObjectMapper());

    @Test
    void queriesCurrentNewsAndWeatherBeforeSendingBriefing() {
        ReminderTask task = task();
        BochaWebSearchResult news = successfulNews();
        when(searchHandler.search("今日热点新闻", "oneDay", 5)).thenReturn(news);
        when(searchHandler.formatReply(news)).thenReturn("1. 今日新闻标题\n   新闻摘要");
        when(weatherReportService.build("杭州", false)).thenReturn(
                new WeatherReportService.WeatherReport(true, "杭州当前晴，气温30°C", null));
        when(mailHandler.send(eq("user@example.com"), eq("资讯简报"), anyString(), eq(false)))
                .thenReturn(MailSendResult.success("user@example.com", "资讯简报", "now"));

        assertThat(executor.execute(task)).isTrue();

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(mailHandler).send(eq("user@example.com"), eq("资讯简报"), content.capture(), eq(false));
        assertThat(content.getValue())
                .contains("资讯简报", "【天气】", "杭州当前晴", "【今日资讯】", "今日新闻标题")
                .doesNotContain("这是今天的天气");
    }

    @Test
    void doesNotSendMailWhenCurrentNewsIsUnavailable() {
        when(searchHandler.search("今日热点新闻", "oneDay", 5)).thenReturn(
                new BochaWebSearchResult(null, null, "今日热点新闻", "oneDay", 5,
                        "API unavailable", List.of()));

        assertThat(executor.execute(task())).isFalse();
        verify(weatherReportService, never()).build(anyString(), anyBoolean());
        verify(mailHandler, never()).send(anyString(), anyString(), anyString(), anyBoolean());
    }

    private ReminderTask task() {
        ReminderTask task = new ReminderTask();
        task.setId(36L);
        task.setActionParams("{\"to\":\"user@example.com\",\"subject\":\"资讯简报\","
                + "\"location\":\"杭州\",\"newsQuery\":\"今日热点新闻\",\"newsCount\":5,"
                + "\"includeForecast\":false}");
        return task;
    }

    private BochaWebSearchResult successfulNews() {
        BochaWebSearchResult.Item item = new BochaWebSearchResult.Item(
                "今日新闻标题", "https://example.com/news", "新闻摘要", null, "示例媒体", "2026-07-29");
        return new BochaWebSearchResult(null, null, "今日热点新闻", "oneDay", 5, null, List.of(item));
    }

}

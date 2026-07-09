package com.dust.wxclawbackfront.ai.chat;

import com.dust.wxclawbackfront.ai.tools.mail.MailTools;
import com.dust.wxclawbackfront.ai.tools.reminder.ReminderTools;
import com.dust.wxclawbackfront.ai.tools.time.TimeTools;
import com.dust.wxclawbackfront.ai.tools.weather.WeatherTools;
import com.dust.wxclawbackfront.ai.tools.search.WebSearchTools;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 工具注册器
 * 集中管理所有可用的 AI 工具
 */
@Component
public class LlmToolRegistry {

    private final TimeTools timeTools;
    private final WeatherTools weatherTools;
    private final WebSearchTools webSearchTools;
    private final ReminderTools reminderTools;
    private final MailTools mailTools;

    public LlmToolRegistry(TimeTools timeTools,
                          WeatherTools weatherTools,
                          WebSearchTools webSearchTools,
                          ReminderTools reminderTools,
                          @Autowired(required = false) MailTools mailTools) {
        this.timeTools = timeTools;
        this.weatherTools = weatherTools;
        this.webSearchTools = webSearchTools;
        this.reminderTools = reminderTools;
        this.mailTools = mailTools;
    }

    /**
     * 获取所有已注册的工具
     * @return 工具数组
     */
    public Object[] getAllTools() {
        List<Object> tools = new ArrayList<>();
        tools.add(timeTools);
        tools.add(weatherTools);
        tools.add(webSearchTools);
        tools.add(reminderTools);
        if (mailTools != null) {
            tools.add(mailTools);
        }
        return tools.toArray();
    }

    public TimeTools getTimeTools() {
        return timeTools;
    }

    public WeatherTools getWeatherTools() {
        return weatherTools;
    }

    public WebSearchTools getWebSearchTools() {
        return webSearchTools;
    }

    public ReminderTools getReminderTools() {
        return reminderTools;
    }

    public MailTools getMailTools() {
        return mailTools;
    }
}

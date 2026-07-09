package com.dust.wxclawbackfront.ai.chat;

import com.dust.wxclawbackfront.ai.tools.reminder.ReminderTools;
import com.dust.wxclawbackfront.ai.tools.time.TimeTools;
import com.dust.wxclawbackfront.ai.tools.weather.WeatherTools;
import com.dust.wxclawbackfront.ai.tools.search.WebSearchTools;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * LLM 工具注册器
 * 集中管理所有可用的 AI 工具
 */
@Component
@RequiredArgsConstructor
public class LlmToolRegistry {

    private final TimeTools timeTools;
    private final WeatherTools weatherTools;
    private final WebSearchTools webSearchTools;
    private final ReminderTools reminderTools;

    /**
     * 获取所有已注册的工具
     * @return 工具数组
     */
    public Object[] getAllTools() {
        return new Object[]{timeTools, weatherTools, webSearchTools, reminderTools};
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
}

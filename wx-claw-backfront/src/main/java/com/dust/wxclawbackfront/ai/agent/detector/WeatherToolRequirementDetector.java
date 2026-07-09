package com.dust.wxclawbackfront.ai.agent.detector;

import com.dust.wxclawbackfront.ai.agent.ToolRequirement;
import com.dust.wxclawbackfront.ai.tools.shared.AiToolInvocationStore;
import com.dust.wxclawbackfront.ai.tools.weather.SeniverseWeatherHandler;
import com.dust.wxclawbackfront.ai.tools.weather.WeatherForecastResult;
import com.dust.wxclawbackfront.ai.tools.weather.WeatherNowResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 天气工具需求检测器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherToolRequirementDetector implements ToolRequirementDetector {

    private static final Pattern WEATHER_PATTERN = Pattern.compile("天气|气温|温度|下雨|下雪|冷不冷|热不热|穿什么|适合出门|带伞");
    private static final Pattern FORECAST_PATTERN = Pattern.compile("明天|后天|未来|这几天|未来[一二三四五六七八九十0-9]+天|周末|下周");
    private static final Pattern LOCATION_BEFORE_WEATHER = Pattern.compile("([\\u4e00-\\u9fa5]{2,12})(?:今天|明天|后天|未来[一二三四五六七八九十0-9]*天|周末|下周)?(?:天气|气温|温度|下雨|下雪|冷不冷|热不热|穿什么|适合出门|带伞)");
    private static final Pattern LOCATION_AFTER_TIME = Pattern.compile("(?:今天|明天|后天|未来[一二三四五六七八九十0-9]*天|周末|下周)([\\u4e00-\\u9fa5]{2,12})(?:天气|气温|温度|下雨|下雪|冷不冷|热不热|穿什么|适合出门|带伞)");

    private final SeniverseWeatherHandler weatherHandler;

    @Override
    public ToolRequirement detect(String userMessage, Set<String> calledTools) {
        if (userMessage == null || userMessage.isBlank()) {
            return null;
        }
        if (!WEATHER_PATTERN.matcher(userMessage).find()) {
            return null;
        }

        String location = extractLocation(userMessage);
        boolean isForecast = FORECAST_PATTERN.matcher(userMessage).find();

        if (isForecast) {
            if (calledTools.contains("weather_forecast")) {
                return null;
            }
            return new ToolRequirement("weather_forecast", location, detectForecastStart(userMessage), detectForecastDays(userMessage));
        } else {
            if (calledTools.contains("weather_now")) {
                return null;
            }
            return new ToolRequirement("weather_now", location, null, null);
        }
    }

    @Override
    public AiToolInvocationStore.Invocation fillTool(ToolRequirement requirement) {
        try {
            if ("weather_now".equals(requirement.toolName())) {
                WeatherNowResult result = weatherHandler.now(requirement.location());
                return new AiToolInvocationStore.Invocation(
                        "weather_now",
                        requirement.location(),
                        weatherHandler.formatReply(result)
                );
            } else if ("weather_forecast".equals(requirement.toolName())) {
                WeatherForecastResult result = weatherHandler.forecast(requirement.location(), requirement.start(), requirement.days());
                String request = "location=" + requirement.location() + ",start=" + requirement.start() + ",days=" + requirement.days();
                return new AiToolInvocationStore.Invocation(
                        "weather_forecast",
                        request,
                        weatherHandler.formatForecastReply(result)
                );
            }
        } catch (Exception e) {
            log.warn("补调天气工具失败: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public String getToolName() {
        return "weather";
    }

    private String extractLocation(String text) {
        String location = matchLocation(LOCATION_AFTER_TIME, text);
        if (location == null) {
            location = matchLocation(LOCATION_BEFORE_WEATHER, text);
        }
        if (location == null || location.isBlank()) {
            return null;
        }
        return stripLocationNoise(location);
    }

    private String matchLocation(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private String stripLocationNoise(String location) {
        String value = location == null ? null : location.trim();
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.replace("今天", "")
                .replace("明天", "")
                .replace("后天", "")
                .replace("未来", "")
                .replace("这几天", "")
                .replace("周末", "")
                .replace("下周", "")
                .trim();
    }

    private Integer detectForecastStart(String text) {
        if (text == null) {
            return 1;
        }
        if (text.contains("后天")) {
            return 2;
        }
        if (text.contains("今天")) {
            return 0;
        }
        return 1;
    }

    private Integer detectForecastDays(String text) {
        if (text == null) {
            return 1;
        }
        if (text.contains("未来三天") || text.contains("未来3天") || text.contains("这三天") || text.contains("这3天")) {
            return 3;
        }
        if (text.contains("未来两天") || text.contains("未来2天") || text.contains("这两天") || text.contains("这2天")) {
            return 2;
        }
        return 1;
    }
}

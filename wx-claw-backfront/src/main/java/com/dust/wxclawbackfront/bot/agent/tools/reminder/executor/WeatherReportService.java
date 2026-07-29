package com.dust.wxclawbackfront.bot.agent.tools.reminder.executor;

import com.dust.wxclawbackfront.bot.agent.tools.weather.SeniverseWeatherHandler;
import com.dust.wxclawbackfront.bot.agent.tools.weather.WeatherForecastResult;
import com.dust.wxclawbackfront.bot.agent.tools.weather.WeatherNowResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WeatherReportService {

    private final SeniverseWeatherHandler weatherHandler;

    public WeatherReport build(String location, boolean includeForecast) {
        WeatherNowResult now = weatherHandler.now(location);
        if (now == null || now.getErrorMsg() != null) {
            return WeatherReport.failure(now == null ? "实时天气返回为空" : now.getErrorMsg());
        }

        StringBuilder content = new StringBuilder(weatherHandler.formatReply(now));
        if (includeForecast) {
            WeatherForecastResult forecast = weatherHandler.forecast(location, 0, 3);
            if (forecast == null || forecast.getErrorMsg() != null) {
                return WeatherReport.failure(forecast == null ? "天气预报返回为空" : forecast.getErrorMsg());
            }
            content.append("\n\n").append(weatherHandler.formatForecastReply(forecast));
        }
        return WeatherReport.success(content.toString());
    }

    public record WeatherReport(boolean success, String content, String errorMessage) {
        static WeatherReport success(String content) {
            return new WeatherReport(true, content, null);
        }

        static WeatherReport failure(String errorMessage) {
            return new WeatherReport(false, null, errorMessage);
        }
    }
}

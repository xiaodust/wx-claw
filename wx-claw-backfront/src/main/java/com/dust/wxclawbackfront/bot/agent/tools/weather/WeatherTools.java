package com.dust.wxclawbackfront.bot.agent.tools.weather;

import com.dust.wxclawbackfront.bot.agent.tools.shared.AiToolProvider;
import com.dust.wxclawbackfront.bot.agent.tools.shared.ToolInvocationLog;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WeatherTools implements AiToolProvider {

    @Override
    public Object getTool() {
        return this;
    }

    @Override
    public int getOrder() {
        return 20;
    }

    private final SeniverseWeatherHandler weatherHandler;

    @Tool(name = "weather_now", description = "获取指定城市的实时天气。适合查询当前、现在、此刻天气。参数 location 支持 beijing/shanghai/hangzhou 或中文城市名。")
    @ToolInvocationLog("weather_now")
    public WeatherNowToolResult now(String location) {
        WeatherNowResult result = weatherHandler.now(location);
        if (result == null) {
            return new WeatherNowToolResult(null, null, null, null, null, null, null, null, null, null);
        }
        if (result.getErrorMsg() != null && !result.getErrorMsg().isBlank()) {
            return new WeatherNowToolResult(null, null, null, null, null, null, null, null, null, result.getErrorMsg());
        }
        return new WeatherNowToolResult(
                result.getLocationName(),
                result.getLocationPath(),
                result.getLastUpdate(),
                result.getText(),
                result.getCode(),
                result.getTemperature(),
                result.getFeelsLike(),
                result.getHumidity(),
                result.getWindDirection(),
                null
        );
    }

    @Tool(name = "weather_forecast", description = "获取指定城市逐日天气预报。适合查询明天、后天、未来几天、周末天气。参数 location 为城市名；start 为起始天数，今天=0、明天=1、后天=2；days 为返回天数，免费账号通常最多3天。")
    @ToolInvocationLog("weather_forecast")
    public WeatherForecastToolResult forecast(String location, Integer start, Integer days) {
        WeatherForecastResult result = weatherHandler.forecast(location, start, days);
        if (result == null) {
            return new WeatherForecastToolResult(null, null, null, null, null);
        }
        if (result.getErrorMsg() != null && !result.getErrorMsg().isBlank()) {
            return new WeatherForecastToolResult(result.getLocationName(), result.getLocationPath(), result.getLastUpdate(), result.getDaily(), result.getErrorMsg());
        }
        return new WeatherForecastToolResult(
                result.getLocationName(),
                result.getLocationPath(),
                result.getLastUpdate(),
                result.getDaily(),
                null
        );
    }

    public record WeatherNowToolResult(String locationName,
                                      String locationPath,
                                      String lastUpdate,
                                      String text,
                                      String code,
                                      String temperature,
                                      String feelsLike,
                                      String humidity,
                                      String windDirection,
                                      String errorMsg) {
    }

    public record WeatherForecastToolResult(String locationName,
                                            String locationPath,
                                            String lastUpdate,
                                            java.util.List<WeatherForecastResult.Daily> daily,
                                            String errorMsg) {
    }
}


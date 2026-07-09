package com.dust.wxclawbackfront.ai.tools.weather;

import com.dust.wxclawbackfront.ai.tools.shared.AiToolInvocationStore;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class WeatherTools {

    private final SeniverseWeatherHandler weatherHandler;
    private final AiToolInvocationStore invocationStore;

    public WeatherTools(SeniverseWeatherHandler weatherHandler, AiToolInvocationStore invocationStore) {
        this.weatherHandler = weatherHandler;
        this.invocationStore = invocationStore;
    }

    @Tool(name = "weather_now", description = "获取指定城市的实时天气。适合查询当前、现在、此刻天气。参数 location 支持 beijing/shanghai/hangzhou 或中文城市名。")
    public WeatherNowToolResult now(String location) {
        WeatherNowResult result = weatherHandler.now(location);
        String response = weatherHandler.formatReply(result);
        invocationStore.add("weather_now", location, response);
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
    public WeatherForecastToolResult forecast(String location, Integer start, Integer days) {
        WeatherForecastResult result = weatherHandler.forecast(location, start, days);
        String response = weatherHandler.formatForecastReply(result);
        invocationStore.add("weather_forecast", "location=" + location + ",start=" + start + ",days=" + days, response);
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


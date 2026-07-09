package com.dust.wxclawbackfront.ai.tools.weather;

import lombok.Getter;

@Getter
public final class WeatherNowResult {

    private final String requestUrl;
    private final String responseJson;
    private final String locationId;
    private final String locationName;
    private final String locationPath;
    private final String timezone;
    private final String lastUpdate;
    private final String text;
    private final String code;
    private final String temperature;
    private final String feelsLike;
    private final String humidity;
    private final String windDirection;
    private final String windScale;
    private final String windSpeed;
    private final String errorMsg;

    public WeatherNowResult(String requestUrl,
                            String responseJson,
                            String locationId,
                            String locationName,
                            String locationPath,
                            String timezone,
                            String lastUpdate,
                            String text,
                            String code,
                            String temperature,
                            String feelsLike,
                            String humidity,
                            String windDirection,
                            String windScale,
                            String windSpeed,
                            String errorMsg) {
        this.requestUrl = requestUrl;
        this.responseJson = responseJson;
        this.locationId = locationId;
        this.locationName = locationName;
        this.locationPath = locationPath;
        this.timezone = timezone;
        this.lastUpdate = lastUpdate;
        this.text = text;
        this.code = code;
        this.temperature = temperature;
        this.feelsLike = feelsLike;
        this.humidity = humidity;
        this.windDirection = windDirection;
        this.windScale = windScale;
        this.windSpeed = windSpeed;
        this.errorMsg = errorMsg;
    }
}


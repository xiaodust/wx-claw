package com.dust.wxclawbackfront.bot.agent.tools.weather;

import lombok.Getter;

import java.util.List;

@Getter
public final class WeatherForecastResult {

    private final String requestUrl;
    private final String responseJson;
    private final String locationId;
    private final String locationName;
    private final String locationPath;
    private final String timezone;
    private final String lastUpdate;
    private final List<Daily> daily;
    private final String errorMsg;

    public WeatherForecastResult(String requestUrl,
                                 String responseJson,
                                 String locationId,
                                 String locationName,
                                 String locationPath,
                                 String timezone,
                                 String lastUpdate,
                                 List<Daily> daily,
                                 String errorMsg) {
        this.requestUrl = requestUrl;
        this.responseJson = responseJson;
        this.locationId = locationId;
        this.locationName = locationName;
        this.locationPath = locationPath;
        this.timezone = timezone;
        this.lastUpdate = lastUpdate;
        this.daily = daily;
        this.errorMsg = errorMsg;
    }

    @Getter
    public static final class Daily {
        private final String date;
        private final String textDay;
        private final String textNight;
        private final String high;
        private final String low;
        private final String rainfall;
        private final String humidity;
        private final String windDirection;
        private final String windScale;
        private final String windSpeed;

        public Daily(String date,
                     String textDay,
                     String textNight,
                     String high,
                     String low,
                     String rainfall,
                     String humidity,
                     String windDirection,
                     String windScale,
                     String windSpeed) {
            this.date = date;
            this.textDay = textDay;
            this.textNight = textNight;
            this.high = high;
            this.low = low;
            this.rainfall = rainfall;
            this.humidity = humidity;
            this.windDirection = windDirection;
            this.windScale = windScale;
            this.windSpeed = windSpeed;
        }
    }
}

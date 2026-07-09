package com.dust.wxclawbackfront.ai.tools.weather;

import com.dust.wxclawbackfront.ai.tools.shared.TextSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class SeniverseWeatherHandler {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String nowUrl;
    private final String dailyUrl;
    private final Duration timeout;
    private final String language;
    private final String unit;
    private final String defaultLocation;
    private final String sourceText;

    public SeniverseWeatherHandler(ObjectMapper objectMapper,
                                  @Value("${wxclaw.ai.weather.seniverse.key:}") String apiKey,
                                  @Value("${wxclaw.ai.weather.seniverse.now-url:https://api.seniverse.com/v3/weather/now.json}") String nowUrl,
                                  @Value("${wxclaw.ai.weather.seniverse.daily-url:https://api.seniverse.com/v3/weather/daily.json}") String dailyUrl,
                                  @Value("${wxclaw.ai.weather.seniverse.timeout:PT10S}") Duration timeout,
                                  @Value("${wxclaw.ai.weather.seniverse.language:zh-Hans}") String language,
                                  @Value("${wxclaw.ai.weather.seniverse.unit:c}") String unit,
                                  @Value("${wxclaw.ai.weather.default-location:beijing}") String defaultLocation,
                                  @Value("${wxclaw.ai.weather.source-text:数据来源：心知天气}") String sourceText) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.apiKey = apiKey;
        this.nowUrl = nowUrl;
        this.dailyUrl = dailyUrl;
        this.timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
        this.language = language;
        this.unit = unit;
        this.defaultLocation = defaultLocation;
        this.sourceText = sourceText;
    }

    public WeatherNowResult now(String locationOrNull) {
        String url = nowUrl == null ? null : nowUrl.trim();
        if (url == null || url.isBlank()) {
            return new WeatherNowResult(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, "未配置天气接口 URL");
        }
        String key = apiKey == null ? null : apiKey.trim();
        if (key == null || key.isBlank()) {
            return new WeatherNowResult(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, "未配置心知天气 API Key（wxclaw.ai.weather.seniverse.key）");
        }

        String loc = locationOrNull == null || locationOrNull.isBlank()
                ? (defaultLocation == null || defaultLocation.isBlank() ? "beijing" : defaultLocation.trim())
                : locationOrNull.trim();

        String actualUrl = buildNowUrl(url, key, loc);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(actualUrl))
                    .timeout(timeout)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            String responseJson = TextSanitizer.sanitizeForPrompt(toPrettyJsonOrRaw(body));
            if (response.statusCode() / 100 != 2) {
                return new WeatherNowResult(actualUrl, responseJson, null, null, null, null, null, null, null, null, null, null, null, null, null, "天气请求失败，HTTP " + response.statusCode());
            }

            SeniverseNowResponse parsed = parseResponse(body);
            SeniverseNowResult first = parsed == null ? null : parsed.first();
            if (first == null || first.now == null || first.location == null) {
                return new WeatherNowResult(actualUrl, responseJson, null, null, null, null, null, null, null, null, null, null, null, null, null, "天气响应解析失败");
            }

            return new WeatherNowResult(
                    actualUrl,
                    responseJson,
                    first.location.id,
                    first.location.name,
                    first.location.path,
                    first.location.timezone,
                    first.last_update,
                    first.now.text,
                    first.now.code,
                    first.now.temperature,
                    first.now.feels_like,
                    first.now.humidity,
                    first.now.wind_direction,
                    first.now.wind_scale,
                    first.now.wind_speed,
                    null
            );
        } catch (Exception ex) {
            return new WeatherNowResult(actualUrl, null, null, null, null, null, null, null, null, null, null, null, null, null, null, ex.getMessage());
        }
    }

    public WeatherForecastResult forecast(String locationOrNull, Integer startOrNull, Integer daysOrNull) {
        String url = dailyUrl == null ? null : dailyUrl.trim();
        if (url == null || url.isBlank()) {
            return new WeatherForecastResult(null, null, null, null, null, null, null, Collections.emptyList(), "未配置天气预报接口 URL");
        }
        String key = apiKey == null ? null : apiKey.trim();
        if (key == null || key.isBlank()) {
            return new WeatherForecastResult(null, null, null, null, null, null, null, Collections.emptyList(), "未配置心知天气 API Key（wxclaw.ai.weather.seniverse.key）");
        }

        String loc = locationOrNull == null || locationOrNull.isBlank()
                ? (defaultLocation == null || defaultLocation.isBlank() ? "beijing" : defaultLocation.trim())
                : locationOrNull.trim();
        int start = startOrNull == null ? 0 : Math.max(-1, startOrNull);
        int days = daysOrNull == null ? 3 : Math.max(1, Math.min(daysOrNull, 15));

        String actualUrl = buildDailyUrl(url, key, loc, start, days);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(actualUrl))
                    .timeout(timeout)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            String responseJson = TextSanitizer.sanitizeForPrompt(toPrettyJsonOrRaw(body));
            if (response.statusCode() / 100 != 2) {
                return new WeatherForecastResult(actualUrl, responseJson, null, null, null, null, null, Collections.emptyList(), "天气预报请求失败，HTTP " + response.statusCode());
            }

            SeniverseDailyResponse parsed = parseDailyResponse(body);
            SeniverseDailyResult first = parsed == null ? null : parsed.first();
            if (first == null || first.location == null || first.daily == null) {
                return new WeatherForecastResult(actualUrl, responseJson, null, null, null, null, null, Collections.emptyList(), "天气预报响应解析失败");
            }

            List<WeatherForecastResult.Daily> daily = new ArrayList<>();
            for (SeniverseDaily item : first.daily) {
                if (item == null) {
                    continue;
                }
                daily.add(new WeatherForecastResult.Daily(
                        item.date,
                        item.text_day,
                        item.text_night,
                        item.high,
                        item.low,
                        item.rainfall,
                        item.humidity,
                        item.wind_direction,
                        item.wind_scale,
                        item.wind_speed
                ));
            }

            return new WeatherForecastResult(
                    actualUrl,
                    responseJson,
                    first.location.id,
                    first.location.name,
                    first.location.path,
                    first.location.timezone,
                    first.last_update,
                    daily,
                    null
            );
        } catch (Exception ex) {
            return new WeatherForecastResult(actualUrl, null, null, null, null, null, null, Collections.emptyList(), ex.getMessage());
        }
    }

    public String formatForecastReply(WeatherForecastResult result) {
        if (result == null) {
            return "天气预报查询失败。";
        }
        if (result.getErrorMsg() != null && !result.getErrorMsg().isBlank()) {
            return "天气预报查询失败：" + result.getErrorMsg();
        }
        if (result.getDaily() == null || result.getDaily().isEmpty()) {
            return "天气预报查询失败：响应中没有预报数据。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(pickLocation(result)).append("天气预报：");
        for (WeatherForecastResult.Daily day : result.getDaily()) {
            sb.append("\n").append(day.getDate()).append("：白天");
            appendBlankDefault(sb, day.getTextDay(), "未知");
            sb.append("，夜间");
            appendBlankDefault(sb, day.getTextNight(), "未知");
            if (day.getLow() != null && !day.getLow().isBlank() && day.getHigh() != null && !day.getHigh().isBlank()) {
                sb.append("，").append(day.getLow().trim()).append("~").append(day.getHigh().trim()).append("°C");
            }
            if (day.getWindDirection() != null && !day.getWindDirection().isBlank()) {
                sb.append("，").append(day.getWindDirection().trim()).append("风");
            }
            if (day.getWindScale() != null && !day.getWindScale().isBlank()) {
                sb.append(day.getWindScale().trim()).append("级");
            }
        }
        if (result.getLastUpdate() != null && !result.getLastUpdate().isBlank()) {
            sb.append("\n更新时间：").append(result.getLastUpdate().trim());
        }
        if (sourceText != null && !sourceText.isBlank()) {
            sb.append("\n").append(sourceText.trim());
        }
        return sb.toString();
    }

    public String formatReply(WeatherNowResult result) {
        if (result == null) {
            return "天气查询失败。";
        }
        if (result.getErrorMsg() != null && !result.getErrorMsg().isBlank()) {
            return "天气查询失败：" + result.getErrorMsg();
        }
        String city = pickLocation(result);
        StringBuilder sb = new StringBuilder();
        sb.append(city).append("当前");
        if (result.getText() != null && !result.getText().isBlank()) {
            sb.append(result.getText().trim());
        }
        if (result.getTemperature() != null && !result.getTemperature().isBlank()) {
            sb.append("，气温").append(result.getTemperature().trim()).append("°");
            if (unit != null && unit.trim().equalsIgnoreCase("f")) {
                sb.append("F");
            } else {
                sb.append("C");
            }
        }
        if (result.getFeelsLike() != null && !result.getFeelsLike().isBlank()) {
            sb.append("，体感").append(result.getFeelsLike().trim()).append("°");
            if (unit != null && unit.trim().equalsIgnoreCase("f")) {
                sb.append("F");
            } else {
                sb.append("C");
            }
        }
        if (result.getHumidity() != null && !result.getHumidity().isBlank()) {
            sb.append("，湿度").append(result.getHumidity().trim()).append("%");
        }
        if (result.getWindDirection() != null && !result.getWindDirection().isBlank()) {
            sb.append("，风向").append(result.getWindDirection().trim());
        }
        if (result.getWindScale() != null && !result.getWindScale().isBlank()) {
            sb.append("，风力").append(result.getWindScale().trim()).append("级");
        }
        if (result.getLastUpdate() != null && !result.getLastUpdate().isBlank()) {
            sb.append("\n更新时间：").append(result.getLastUpdate().trim());
        }
        if (sourceText != null && !sourceText.isBlank()) {
            sb.append("\n").append(sourceText.trim());
        }
        return sb.toString();
    }

    public String formatReplyForVoice(WeatherNowResult result) {
        if (result == null) {
            return "天气查询失败。";
        }
        if (result.getErrorMsg() != null && !result.getErrorMsg().isBlank()) {
            return "天气查询失败：" + result.getErrorMsg();
        }
        String city = pickLocation(result);
        StringBuilder sb = new StringBuilder();
        sb.append(city).append("现在");
        if (result.getText() != null && !result.getText().isBlank()) {
            sb.append(result.getText().trim());
        }
        if (result.getTemperature() != null && !result.getTemperature().isBlank()) {
            sb.append("，气温").append(result.getTemperature().trim()).append("度");
        }
        if (result.getFeelsLike() != null && !result.getFeelsLike().isBlank()) {
            sb.append("，体感").append(result.getFeelsLike().trim()).append("度");
        }
        if (result.getHumidity() != null && !result.getHumidity().isBlank()) {
            sb.append("，湿度").append(result.getHumidity().trim()).append("%");
        }
        if (result.getWindDirection() != null && !result.getWindDirection().isBlank()) {
            sb.append("，").append(result.getWindDirection().trim()).append("风");
        }
        if (result.getWindScale() != null && !result.getWindScale().isBlank()) {
            sb.append(result.getWindScale().trim()).append("级");
        }
        return sb.toString();
    }

    private String pickLocation(WeatherNowResult result) {
        if (result.getLocationPath() != null && !result.getLocationPath().isBlank()) {
            return result.getLocationPath().trim() + " ";
        }
        if (result.getLocationName() != null && !result.getLocationName().isBlank()) {
            return result.getLocationName().trim() + " ";
        }
        return "";
    }

    private String pickLocation(WeatherForecastResult result) {
        if (result.getLocationPath() != null && !result.getLocationPath().isBlank()) {
            return result.getLocationPath().trim() + " ";
        }
        if (result.getLocationName() != null && !result.getLocationName().isBlank()) {
            return result.getLocationName().trim() + " ";
        }
        return "";
    }

    private void appendBlankDefault(StringBuilder sb, String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            sb.append(defaultValue);
        } else {
            sb.append(value.trim());
        }
    }

    private String buildNowUrl(String baseUrl, String key, String location) {
        String encodedLoc = URLEncoder.encode(location, StandardCharsets.UTF_8);
        String lang = language == null || language.isBlank() ? "zh-Hans" : language.trim();
        String u = unit == null || unit.isBlank() ? "c" : unit.trim();
        if (baseUrl.contains("?")) {
            return baseUrl
                    + "&key=" + URLEncoder.encode(key, StandardCharsets.UTF_8)
                    + "&location=" + encodedLoc
                    + "&language=" + URLEncoder.encode(lang, StandardCharsets.UTF_8)
                    + "&unit=" + URLEncoder.encode(u, StandardCharsets.UTF_8);
        }
        return baseUrl
                + "?key=" + URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "&location=" + encodedLoc
                + "&language=" + URLEncoder.encode(lang, StandardCharsets.UTF_8)
                + "&unit=" + URLEncoder.encode(u, StandardCharsets.UTF_8);
    }

    private String buildDailyUrl(String baseUrl, String key, String location, int start, int days) {
        String encodedLoc = URLEncoder.encode(location, StandardCharsets.UTF_8);
        String lang = language == null || language.isBlank() ? "zh-Hans" : language.trim();
        String u = unit == null || unit.isBlank() ? "c" : unit.trim();
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl
                + separator + "key=" + URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "&location=" + encodedLoc
                + "&language=" + URLEncoder.encode(lang, StandardCharsets.UTF_8)
                + "&unit=" + URLEncoder.encode(u, StandardCharsets.UTF_8)
                + "&start=" + start
                + "&days=" + days;
    }

    private SeniverseNowResponse parseResponse(String text) {
        try {
            return objectMapper.readValue(text, SeniverseNowResponse.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private SeniverseDailyResponse parseDailyResponse(String text) {
        try {
            return objectMapper.readValue(text, SeniverseDailyResponse.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private String toPrettyJsonOrRaw(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        try {
            Object any = objectMapper.readValue(text, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(any);
        } catch (Exception ignore) {
            return text;
        }
    }

    private static class SeniverseNowResponse {
        public SeniverseNowResult[] results;

        public SeniverseNowResult first() {
            if (results == null || results.length == 0) {
                return null;
            }
            return results[0];
        }
    }

    private static class SeniverseNowResult {
        public SeniverseLocation location;
        public SeniverseNow now;
        public String last_update;
    }

    private static class SeniverseDailyResponse {
        public SeniverseDailyResult[] results;

        public SeniverseDailyResult first() {
            if (results == null || results.length == 0) {
                return null;
            }
            return results[0];
        }
    }

    private static class SeniverseDailyResult {
        public SeniverseLocation location;
        public SeniverseDaily[] daily;
        public String last_update;
    }

    private static class SeniverseDaily {
        public String date;
        public String text_day;
        public String code_day;
        public String text_night;
        public String code_night;
        public String high;
        public String low;
        public String precip;
        public String wind_direction;
        public String wind_direction_degree;
        public String wind_speed;
        public String wind_scale;
        public String rainfall;
        public String humidity;
    }

    private static class SeniverseLocation {
        public String id;
        public String name;
        public String country;
        public String path;
        public String timezone;
        public String timezone_offset;
    }

    private static class SeniverseNow {
        public String text;
        public String code;
        public String temperature;
        public String feels_like;
        public String pressure;
        public String humidity;
        public String visibility;
        public String wind_direction;
        public String wind_scale;
        public String wind_speed;
    }
}

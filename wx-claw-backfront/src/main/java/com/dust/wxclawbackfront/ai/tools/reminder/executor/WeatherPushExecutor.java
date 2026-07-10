package com.dust.wxclawbackfront.ai.tools.reminder.executor;

import com.dust.wxclawbackfront.ai.tools.reminder.ReminderTask;
import com.dust.wxclawbackfront.ai.tools.weather.SeniverseWeatherHandler;
import com.dust.wxclawbackfront.ai.tools.weather.WeatherForecastResult;
import com.dust.wxclawbackfront.ai.tools.weather.WeatherNowResult;
import com.dust.wxclawbackfront.ilnk.outbound.ILinkMessageSender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 天气推送执行器
 * 定时推送天气信息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherPushExecutor implements TaskActionExecutor {
    
    private final SeniverseWeatherHandler weatherHandler;
    private final ILinkMessageSender messageSender;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public boolean execute(ReminderTask task) {
        try {
            String actionParams = task.getActionParams();
            if (actionParams == null || actionParams.isBlank()) {
                log.error("天气推送任务参数为空: taskId={}", task.getId());
                return false;
            }
            
            // 解析参数
            JsonNode params = objectMapper.readTree(actionParams);
            String location = params.get("location").asText();
            boolean includeForecast = params.has("includeForecast") && params.get("includeForecast").asBoolean();
            
            log.info("执行天气推送任务: taskId={}, userId={}, location={}", 
                    task.getId(), task.getUserId(), location);
            
            // 获取天气信息
            WeatherNowResult nowResult = weatherHandler.now(location);
            WeatherForecastResult forecastResult = includeForecast ? weatherHandler.forecast(location, 0, 3) : null;
            
            // 格式化消息
            String message = formatWeatherMessage(location, nowResult, forecastResult);
            
            // 发送消息
            messageSender.sendText(task.getUserId(), message);
            
            log.info("天气推送成功: taskId={}, userId={}, location={}", task.getId(), task.getUserId(), location);
            return true;
            
        } catch (Exception e) {
            log.error("天气推送任务执行异常: taskId={}, error={}", task.getId(), e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public String getActionType() {
        return "WEATHER_PUSH";
    }
    
    /**
     * 格式化天气消息
     */
    private String formatWeatherMessage(String location, WeatherNowResult now, WeatherForecastResult forecast) {
        StringBuilder sb = new StringBuilder();
        sb.append("🌤️ ").append(location).append("天气播报\n\n");
        
        if (now != null && now.getErrorMsg() == null) {
            sb.append("【当前天气】\n");
            if (now.getText() != null) sb.append("天气：").append(now.getText()).append("\n");
            if (now.getTemperature() != null) sb.append("温度：").append(now.getTemperature()).append("℃\n");
            if (now.getFeelsLike() != null) sb.append("体感温度：").append(now.getFeelsLike()).append("℃\n");
            if (now.getHumidity() != null) sb.append("湿度：").append(now.getHumidity()).append("%\n");
            if (now.getWindDirection() != null) {
                sb.append("风向：").append(now.getWindDirection());
                if (now.getWindScale() != null) {
                    sb.append(" ").append(now.getWindScale()).append("级");
                }
                sb.append("\n");
            }
        }
        
        if (forecast != null && forecast.getErrorMsg() == null && forecast.getDaily() != null && !forecast.getDaily().isEmpty()) {
            sb.append("\n【未来天气】\n");
            forecast.getDaily().forEach(day -> {
                sb.append(day.getDate()).append("：")
                  .append(day.getTextDay()).append(" ")
                  .append(day.getLow()).append("~").append(day.getHigh()).append("℃\n");
            });
        }
        
        return sb.toString();
    }
}

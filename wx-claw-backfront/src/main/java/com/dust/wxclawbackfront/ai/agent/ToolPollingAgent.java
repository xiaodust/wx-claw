package com.dust.wxclawbackfront.ai.agent;

import com.dust.wxclawbackfront.ai.tools.shared.AiToolInvocationStore;
import com.dust.wxclawbackfront.ai.tools.shared.TextSanitizer;
import com.dust.wxclawbackfront.ai.tools.time.TimeHandler;
import com.dust.wxclawbackfront.ai.tools.time.TimeResult;
import com.dust.wxclawbackfront.ai.tools.weather.SeniverseWeatherHandler;
import com.dust.wxclawbackfront.ai.tools.weather.WeatherForecastResult;
import com.dust.wxclawbackfront.ai.tools.weather.WeatherNowResult;
import com.dust.wxclawbackfront.ai.tools.web.BochaWebSearchHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class ToolPollingAgent {

    private static final Pattern TIME_PATTERN = Pattern.compile("(现在|此刻|当前)?(几点|时间|日期|几号|星期几|周几)|今天(几号|日期)|今天是(几号|什么日期)");
    private static final Pattern WEATHER_PATTERN = Pattern.compile("天气|气温|温度|下雨|下雪|冷不冷|热不热|穿什么|适合出门|带伞");
    private static final Pattern FORECAST_PATTERN = Pattern.compile("明天|后天|未来|这几天|未来[一二三四五六七八九十0-9]+天|周末|下周");
    private static final Pattern WEB_SEARCH_PATTERN = Pattern.compile("联网|上网查|网上查|搜索|搜一下|查一下|新闻|最新|最近|公开资料|百科|官网|官方公告|实时资讯");
    private static final Pattern LOCATION_BEFORE_WEATHER = Pattern.compile("([\\u4e00-\\u9fa5]{2,12})(?:今天|明天|后天|未来[一二三四五六七八九十0-9]*天|周末|下周)?(?:天气|气温|温度|下雨|下雪|冷不冷|热不热|穿什么|适合出门|带伞)");
    private static final Pattern LOCATION_AFTER_TIME = Pattern.compile("(?:今天|明天|后天|未来[一二三四五六七八九十0-9]*天|周末|下周)([\\u4e00-\\u9fa5]{2,12})(?:天气|气温|温度|下雨|下雪|冷不冷|热不热|穿什么|适合出门|带伞)");

    private final TimeHandler timeHandler;
    private final SeniverseWeatherHandler weatherHandler;
    private final BochaWebSearchHandler webSearchHandler;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final int maxRounds;
    private final boolean directToolFill;

    public ToolPollingAgent(TimeHandler timeHandler,
                            SeniverseWeatherHandler weatherHandler,
                            BochaWebSearchHandler webSearchHandler,
                            ObjectMapper objectMapper,
                            @Value("${wxclaw.ai.agent.enabled:true}") boolean enabled,
                            @Value("${wxclaw.ai.agent.max-rounds:2}") int maxRounds,
                            @Value("${wxclaw.ai.agent.direct-tool-fill:true}") boolean directToolFill) {
        this.timeHandler = timeHandler;
        this.weatherHandler = weatherHandler;
        this.webSearchHandler = webSearchHandler;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.maxRounds = Math.max(1, maxRounds);
        this.directToolFill = directToolFill;
    }

    public AgentChatResult run(String userMessage, String firstPrompt, AgentLlmCaller llmCaller) {
        List<AiToolInvocationStore.Invocation> allInvocations = new ArrayList<>();
        List<AgentChatRound> rounds = new ArrayList<>();

        AgentLlmCaller.LlmCallResult first = llmCaller.call(firstPrompt);
        String finalContent = first.content();
        allInvocations.addAll(first.invocations());
        rounds.add(new AgentChatRound(1, "llm", firstPrompt, first.content(), first.invocations(), null));

        if (enabled && maxRounds > 1) {
            for (int round = 2; round <= maxRounds; round++) {
                List<ToolRequirement> missing = detectMissingRequirements(userMessage, allInvocations);
                if (missing.isEmpty()) {
                    break;
                }

                List<AiToolInvocationStore.Invocation> filled = directToolFill ? fillMissingTools(userMessage, missing) : List.of();
                allInvocations.addAll(filled);

                String supplementPrompt = buildSupplementPrompt(userMessage, finalContent, allInvocations, missing, filled);
                AgentLlmCaller.LlmCallResult next = llmCaller.call(supplementPrompt);
                finalContent = next.content();
                allInvocations.addAll(next.invocations());

                Map<String, Object> reason = new LinkedHashMap<>();
                reason.put("missing", missing.stream().map(ToolRequirement::toolName).toList());
                reason.put("directToolFill", directToolFill);
                reason.put("directFilled", filled.stream().map(AiToolInvocationStore.Invocation::toolName).toList());
                rounds.add(new AgentChatRound(round, "agent_supplement", supplementPrompt, next.content(), next.invocations(), reason));
            }
        }

        boolean completed = detectMissingRequirements(userMessage, allInvocations).isEmpty();
        return new AgentChatResult(finalContent, allInvocations, rounds, completed);
    }

    public String toJsonSafely(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }

    public String joinToolNames(List<AiToolInvocationStore.Invocation> invocations) {
        if (invocations == null || invocations.isEmpty()) {
            return null;
        }
        return invocations.stream()
                .map(AiToolInvocationStore.Invocation::toolName)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .collect(Collectors.joining(","));
    }

    private List<ToolRequirement> detectMissingRequirements(String userMessage, List<AiToolInvocationStore.Invocation> invocations) {
        String text = userMessage == null ? "" : userMessage.trim();
        if (text.isBlank()) {
            return List.of();
        }
        Set<String> calledTools = invocations == null ? Set.of() : invocations.stream()
                .filter(Objects::nonNull)
                .map(AiToolInvocationStore.Invocation::toolName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<ToolRequirement> missing = new ArrayList<>();

        if (TIME_PATTERN.matcher(text).find() && !calledTools.contains("time_now")) {
            missing.add(new ToolRequirement("time_now", null, null, null));
        }

        boolean weatherQuestion = WEATHER_PATTERN.matcher(text).find();
        if (weatherQuestion) {
            String location = extractLocation(text);
            boolean forecast = FORECAST_PATTERN.matcher(text).find();
            if (forecast) {
                if (!calledTools.contains("weather_forecast")) {
                    missing.add(new ToolRequirement("weather_forecast", location, detectForecastStart(text), detectForecastDays(text)));
                }
            } else if (!calledTools.contains("weather_now")) {
                missing.add(new ToolRequirement("weather_now", location, null, null));
            }
        }

        if (shouldUseWebSearch(text) && !calledTools.contains("web_search")) {
            missing.add(new ToolRequirement("web_search", null, null, null));
        }

        return missing;
    }

    private List<AiToolInvocationStore.Invocation> fillMissingTools(String userMessage, List<ToolRequirement> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return List.of();
        }
        List<AiToolInvocationStore.Invocation> result = new ArrayList<>();
        for (ToolRequirement requirement : requirements) {
            if ("time_now".equals(requirement.toolName())) {
                TimeResult time = timeHandler.now();
                String response = time == null ? "时间查询失败。" : (time.getErrorMsg() == null ? time.getReplyText() : "时间查询失败：" + time.getErrorMsg());
                result.add(new AiToolInvocationStore.Invocation("time_now", "{}", response));
            } else if ("weather_now".equals(requirement.toolName())) {
                WeatherNowResult weather = weatherHandler.now(requirement.location());
                result.add(new AiToolInvocationStore.Invocation("weather_now", requirement.location(), weatherHandler.formatReply(weather)));
            } else if ("weather_forecast".equals(requirement.toolName())) {
                WeatherForecastResult forecast = weatherHandler.forecast(requirement.location(), requirement.start(), requirement.days());
                String request = "location=" + requirement.location() + ",start=" + requirement.start() + ",days=" + requirement.days();
                result.add(new AiToolInvocationStore.Invocation("weather_forecast", request, weatherHandler.formatForecastReply(forecast)));
            } else if ("web_search".equals(requirement.toolName())) {
                String query = buildWebSearchQuery(userMessage);
                var search = webSearchHandler.search(query, "noLimit", 5);
                result.add(new AiToolInvocationStore.Invocation("web_search", "query=" + query + ",freshness=noLimit,count=5", webSearchHandler.formatReply(search)));
            }
        }
        return result;
    }

    private String buildSupplementPrompt(String userMessage,
                                         String previousAnswer,
                                         List<AiToolInvocationStore.Invocation> allInvocations,
                                         List<ToolRequirement> missing,
                                         List<AiToolInvocationStore.Invocation> filled) {
        StringBuilder sb = new StringBuilder();
        sb.append("你刚才的回答可能不完整。请基于补充工具结果，重新给出完整、自然、简洁的中文回答。\n");
        sb.append("不要解释工具调用过程，不要说自己之前漏查。\n\n");
        sb.append("原始用户问题：\n").append(TextSanitizer.sanitizeForPrompt(userMessage)).append("\n\n");
        sb.append("上一轮回答：\n").append(TextSanitizer.sanitizeForPrompt(previousAnswer)).append("\n\n");
        sb.append("系统判断缺少的工具：\n").append(toJsonSafely(missing)).append("\n\n");
        if (filled != null && !filled.isEmpty()) {
            sb.append("已由系统补充调用的工具结果：\n").append(toJsonSafely(filled)).append("\n\n");
        }
        sb.append("全部已知工具调用记录：\n").append(toJsonSafely(allInvocations)).append("\n\n");
        sb.append("请直接给用户最终回复。");
        return sb.toString();
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

    private boolean shouldUseWebSearch(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        if (WEATHER_PATTERN.matcher(text).find() || TIME_PATTERN.matcher(text).find()) {
            return false;
        }
        return WEB_SEARCH_PATTERN.matcher(text).find();
    }

    private String buildWebSearchQuery(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return "最新公开资料";
        }
        String text = userMessage.trim();
        return text.replace("联网", "")
                .replace("上网查", "")
                .replace("网上查", "")
                .replace("搜索", "")
                .replace("搜一下", "")
                .replace("查一下", "")
                .trim();
    }
}

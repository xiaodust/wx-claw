package com.dust.wxclawbackfront.ai.agent.detector;

import com.dust.wxclawbackfront.ai.agent.ToolRequirement;
import com.dust.wxclawbackfront.ai.tools.search.BochaWebSearchHandler;
import com.dust.wxclawbackfront.ai.tools.shared.AiToolInvocationStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 联网搜索工具需求检测器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSearchToolRequirementDetector implements ToolRequirementDetector {

    private static final Pattern WEB_SEARCH_PATTERN = Pattern.compile(
            "联网|上网查|网上查|搜索|搜一下|查一下|新闻|最新|最近|公开资料|百科|官网|官方公告|实时资讯");

    private static final Pattern WEATHER_PATTERN = Pattern.compile("天气|气温|温度|下雨|下雪|冷不冷|热不热|穿什么|适合出门|带伞");
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "(现在|此刻|当前)?(几点|时间|日期|几号|星期几|周几)|今天(几号|日期)|今天是(几号|什么日期)");

    private final BochaWebSearchHandler webSearchHandler;

    @Override
    public ToolRequirement detect(String userMessage, Set<String> calledTools) {
        if (userMessage == null || userMessage.isBlank()) {
            return null;
        }
        if (calledTools.contains("web_search")) {
            return null;
        }
        if (!shouldUseWebSearch(userMessage)) {
            return null;
        }
        return new ToolRequirement("web_search", null, null, null);
    }

    @Override
    public AiToolInvocationStore.Invocation fillTool(ToolRequirement requirement) {
        // 这里需要 userMessage，但 ToolRequirement 没有传递，需要改造
        // 暂时返回 null，表示不支持直接补调
        log.warn("WebSearch 需要用户原始消息才能生成查询词，当前不支持直接补调");
        return null;
    }

    @Override
    public String getToolName() {
        return "web_search";
    }

    private boolean shouldUseWebSearch(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        // 排除天气和时间相关问题
        if (WEATHER_PATTERN.matcher(text).find() || TIME_PATTERN.matcher(text).find()) {
            return false;
        }
        return WEB_SEARCH_PATTERN.matcher(text).find();
    }

    /**
     * 生成搜索查询词（需要外部调用）
     */
    public String buildSearchQuery(String userMessage) {
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

    /**
     * 执行搜索（供外部调用）
     */
    public AiToolInvocationStore.Invocation executeSearch(String query) {
        try {
            var result = webSearchHandler.search(query, "noLimit", 5);
            String reply = webSearchHandler.formatReply(result);
            return new AiToolInvocationStore.Invocation(
                    "web_search",
                    String.format("query=%s, freshness=noLimit, count=5", query),
                    reply
            );
        } catch (Exception e) {
            log.warn("补调 web_search 失败: {}", e.getMessage());
            return null;
        }
    }
}

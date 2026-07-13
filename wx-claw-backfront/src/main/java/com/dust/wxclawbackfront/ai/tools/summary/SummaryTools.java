package com.dust.wxclawbackfront.ai.tools.summary;

import com.dust.wxclawbackfront.ai.tools.shared.AiToolInvocationStore;
import com.dust.wxclawbackfront.ai.tools.shared.AiToolProvider;
import com.dust.wxclawbackfront.ai.tools.shared.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 对话总结 AI 工具
 * 支持即时总结和定时总结
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SummaryTools implements AiToolProvider {

    @Override
    public Object getTool() {
        return this;
    }

    @Override
    public int getOrder() {
        return 70;
    }

    private final SummaryHandler summaryHandler;
    private final AiToolInvocationStore invocationStore;

    /**
     * 即时生成日报
     */
    @Tool(name = "generate_daily_summary_now",
          description = "【必须调用】立即生成用户昨日的对话日报。当用户要求总结对话、生成日报时，必须调用此工具，不要自行根据上下文总结。此工具会从数据库查询完整的历史对话记录进行总结，而不仅仅是当前上下文的内容。无需任何参数。")
    public SummaryToolResult generateDailySummaryNow() {
        String userId = UserContextHolder.getUserId();
        
        log.info("AI调用 generate_daily_summary_now: userId={}", userId);

        if (userId == null || userId.isBlank()) {
            String errorMsg = "无法获取用户ID";
            invocationStore.add("generate_daily_summary_now", "无参数", errorMsg);
            return new SummaryToolResult(false, null, errorMsg);
        }

        SummaryHandler.SummaryResult result = summaryHandler.generateDailySummaryNow(userId);
        
        invocationStore.add("generate_daily_summary_now", "无参数", result.message());

        return new SummaryToolResult(result.success(), result.success() ? "DAILY" : null, result.message());
    }

    /**
     * 即时生成周报
     */
    @Tool(name = "generate_weekly_summary_now",
          description = "【必须调用】立即生成用户上周的对话周报。当用户要求总结上周对话、生成周报时，必须调用此工具，不要自行根据上下文总结。此工具会从数据库查询完整的历史对话记录进行总结。无需任何参数。")
    public SummaryToolResult generateWeeklySummaryNow() {
        String userId = UserContextHolder.getUserId();
        
        log.info("AI调用 generate_weekly_summary_now: userId={}", userId);

        if (userId == null || userId.isBlank()) {
            String errorMsg = "无法获取用户ID";
            invocationStore.add("generate_weekly_summary_now", "无参数", errorMsg);
            return new SummaryToolResult(false, null, errorMsg);
        }

        SummaryHandler.SummaryResult result = summaryHandler.generateWeeklySummaryNow(userId);
        
        invocationStore.add("generate_weekly_summary_now", "无参数", result.message());

        return new SummaryToolResult(result.success(), result.success() ? "WEEKLY" : null, result.message());
    }

    /**
     * 即时生成月报
     */
    @Tool(name = "generate_monthly_summary_now",
          description = "【必须调用】立即生成用户上月的对话月报。当用户要求总结上月对话、生成月报时，必须调用此工具，不要自行根据上下文总结。此工具会从数据库查询完整的历史对话记录进行总结。无需任何参数。")
    public SummaryToolResult generateMonthlySummaryNow() {
        String userId = UserContextHolder.getUserId();
        
        log.info("AI调用 generate_monthly_summary_now: userId={}", userId);

        if (userId == null || userId.isBlank()) {
            String errorMsg = "无法获取用户ID";
            invocationStore.add("generate_monthly_summary_now", "无参数", errorMsg);
            return new SummaryToolResult(false, null, errorMsg);
        }

        SummaryHandler.SummaryResult result = summaryHandler.generateMonthlySummaryNow(userId);
        
        invocationStore.add("generate_monthly_summary_now", "无参数", result.message());

        return new SummaryToolResult(result.success(), result.success() ? "MONTHLY" : null, result.message());
    }

    /**
     * 即时生成自定义时间范围总结
     */
    @Tool(name = "generate_custom_summary_now",
          description = "【必须调用】立即生成指定时间范围内的对话总结。当用户要求总结过去某段时间的对话时，必须调用此工具，不要自行根据上下文总结。此工具会从数据库查询完整的历史对话记录进行总结。参数 hours 是要总结的小时数（1-720，最多30天）。")
    public SummaryToolResult generateCustomSummaryNow(int hours) {
        String userId = UserContextHolder.getUserId();
        
        log.info("AI调用 generate_custom_summary_now: userId={}, hours={}", userId, hours);

        if (userId == null || userId.isBlank()) {
            String errorMsg = "无法获取用户ID";
            invocationStore.add("generate_custom_summary_now", "hours=" + hours, errorMsg);
            return new SummaryToolResult(false, null, errorMsg);
        }

        SummaryHandler.SummaryResult result = summaryHandler.generateCustomSummaryNow(userId, hours);
        
        invocationStore.add("generate_custom_summary_now", "hours=" + hours, result.message());

        return new SummaryToolResult(result.success(), result.success() ? "CUSTOM" : null, result.message());
    }

    /**
     * 创建定时日报任务
     */
    @Tool(name = "schedule_daily_summary",
          description = "创建每日定时对话总结任务（日报）。AI会在每天指定时间自动总结前一天的对话内容并推送。当用户说'每天X点给我发日报'、'设置每日总结'等时调用。参数：hour(小时0-23)、minute(分钟0-59)。")
    public SummaryScheduleToolResult scheduleDailySummary(int hour, int minute) {
        String userId = UserContextHolder.getUserId();
        
        log.info("AI调用 schedule_daily_summary: userId={}, hour={}, minute={}", userId, hour, minute);

        if (userId == null || userId.isBlank()) {
            String errorMsg = "无法获取用户ID";
            invocationStore.add("schedule_daily_summary", 
                    String.format("hour=%d, minute=%d", hour, minute), errorMsg);
            return new SummaryScheduleToolResult(false, null, errorMsg);
        }

        SummaryHandler.ReminderCreateResult result = summaryHandler.createDailySummarySchedule(userId, hour, minute);
        
        invocationStore.add("schedule_daily_summary",
                String.format("hour=%d, minute=%d", hour, minute),
                result.message());

        return new SummaryScheduleToolResult(result.success(), result.reminderId(), result.message());
    }

    /**
     * 创建定时周报任务
     */
    @Tool(name = "schedule_weekly_summary",
          description = "创建每周定时对话总结任务（周报）。AI会在每周指定时间自动总结上周的对话内容并推送。当用户说'每周X给我发周报'、'设置每周总结'等时调用。参数：dayOfWeek(1-7，1=周一，7=周日)、hour(小时0-23)、minute(分钟0-59)。")
    public SummaryScheduleToolResult scheduleWeeklySummary(int dayOfWeek, int hour, int minute) {
        String userId = UserContextHolder.getUserId();
        
        log.info("AI调用 schedule_weekly_summary: userId={}, dayOfWeek={}, hour={}, minute={}", 
                userId, dayOfWeek, hour, minute);

        if (userId == null || userId.isBlank()) {
            String errorMsg = "无法获取用户ID";
            invocationStore.add("schedule_weekly_summary", 
                    String.format("dayOfWeek=%d, hour=%d, minute=%d", dayOfWeek, hour, minute), errorMsg);
            return new SummaryScheduleToolResult(false, null, errorMsg);
        }

        SummaryHandler.ReminderCreateResult result = summaryHandler.createWeeklySummarySchedule(userId, dayOfWeek, hour, minute);
        
        invocationStore.add("schedule_weekly_summary",
                String.format("dayOfWeek=%d, hour=%d, minute=%d", dayOfWeek, hour, minute),
                result.message());

        return new SummaryScheduleToolResult(result.success(), result.reminderId(), result.message());
    }

    /**
     * 创建定时月报任务
     */
    @Tool(name = "schedule_monthly_summary",
          description = "创建每月定时对话总结任务（月报）。AI会在每月指定日期自动总结上月的对话内容并推送。当用户说'每月X号给我发月报'、'设置每月总结'等时调用。参数：dayOfMonth(每月的第几天，1-28)、hour(小时0-23)、minute(分钟0-59)。")
    public SummaryScheduleToolResult scheduleMonthlySummary(int dayOfMonth, int hour, int minute) {
        String userId = UserContextHolder.getUserId();
        
        log.info("AI调用 schedule_monthly_summary: userId={}, dayOfMonth={}, hour={}, minute={}", 
                userId, dayOfMonth, hour, minute);

        if (userId == null || userId.isBlank()) {
            String errorMsg = "无法获取用户ID";
            invocationStore.add("schedule_monthly_summary", 
                    String.format("dayOfMonth=%d, hour=%d, minute=%d", dayOfMonth, hour, minute), errorMsg);
            return new SummaryScheduleToolResult(false, null, errorMsg);
        }

        SummaryHandler.ReminderCreateResult result = summaryHandler.createMonthlySummarySchedule(userId, dayOfMonth, hour, minute);
        
        invocationStore.add("schedule_monthly_summary",
                String.format("dayOfMonth=%d, hour=%d, minute=%d", dayOfMonth, hour, minute),
                result.message());

        return new SummaryScheduleToolResult(result.success(), result.reminderId(), result.message());
    }

    /**
     * 总结工具结果
     */
    public record SummaryToolResult(boolean success, String summaryType, String message) {}

    /**
     * 定时总结工具结果
     */
    public record SummaryScheduleToolResult(boolean success, Long reminderId, String message) {}
}

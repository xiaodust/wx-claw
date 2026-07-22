package com.dust.wxclawbackfront.bot.agent.tools.summary;

import com.dust.wxclawbackfront.bot.agent.tools.shared.AiToolInvocationStore;
import com.dust.wxclawbackfront.bot.agent.tools.shared.AiToolProvider;
import com.dust.wxclawbackfront.bot.agent.tools.shared.ToolInvocationLog;
import com.dust.wxclawbackfront.bot.agent.tools.shared.UserContextHolder;
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
          description = "生成昨日对话日报。仅当用户明确要求'总结昨天'、'生成日报'、'总结今天的对话'时调用。此工具会从数据库查询历史记录进行完整总结。注意：普通聊天问候不要调用此工具。")
    @ToolInvocationLog("generate_daily_summary_now")
    public SummaryToolResult generateDailySummaryNow() {
        String userId = UserContextHolder.getUserId();

        if (userId == null || userId.isBlank()) {
            return new SummaryToolResult(false, null, "无法获取用户ID");
        }

        SummaryHandler.SummaryResult result = summaryHandler.generateDailySummaryNow(userId);

        return new SummaryToolResult(result.success(), result.success() ? "DAILY" : null, result.message());
    }

    /**
     * 即时生成周报
     */
    @Tool(name = "generate_weekly_summary_now",
          description = "生成上周对话周报。仅当用户明确要求'总结上周'、'生成周报'时调用。此工具会从数据库查询历史记录进行完整总结。注意：普通聊天问候不要调用此工具。")
    @ToolInvocationLog("generate_weekly_summary_now")
    public SummaryToolResult generateWeeklySummaryNow() {
        String userId = UserContextHolder.getUserId();

        if (userId == null || userId.isBlank()) {
            return new SummaryToolResult(false, null, "无法获取用户ID");
        }

        SummaryHandler.SummaryResult result = summaryHandler.generateWeeklySummaryNow(userId);

        return new SummaryToolResult(result.success(), result.success() ? "WEEKLY" : null, result.message());
    }

    /**
     * 即时生成月报
     */
    @Tool(name = "generate_monthly_summary_now",
          description = "生成上月对话月报。仅当用户明确要求'总结上个月'、'生成月报'时调用。此工具会从数据库查询历史记录进行完整总结。注意：普通聊天问候不要调用此工具。")
    @ToolInvocationLog("generate_monthly_summary_now")
    public SummaryToolResult generateMonthlySummaryNow() {
        String userId = UserContextHolder.getUserId();

        if (userId == null || userId.isBlank()) {
            return new SummaryToolResult(false, null, "无法获取用户ID");
        }

        SummaryHandler.SummaryResult result = summaryHandler.generateMonthlySummaryNow(userId);

        return new SummaryToolResult(result.success(), result.success() ? "MONTHLY" : null, result.message());
    }

    /**
     * 即时生成自定义时间范围总结
     */
    @Tool(name = "generate_custom_summary_now",
          description = "生成指定时间范围内的对话总结。仅当用户明确要求'总结过去X小时'、'最近X小时聊了什么'时调用。参数hours是要总结的小时数（1-720）。注意：普通聊天问候不要调用此工具。")
    @ToolInvocationLog("generate_custom_summary_now")
    public SummaryToolResult generateCustomSummaryNow(int hours) {
        String userId = UserContextHolder.getUserId();

        if (userId == null || userId.isBlank()) {
            return new SummaryToolResult(false, null, "无法获取用户ID");
        }

        SummaryHandler.SummaryResult result = summaryHandler.generateCustomSummaryNow(userId, hours);

        return new SummaryToolResult(result.success(), result.success() ? "CUSTOM" : null, result.message());
    }

    /**
     * 创建定时日报任务
     */
    @Tool(name = "schedule_daily_summary",
          description = "创建每日定时对话总结任务（日报）。AI会在每天指定时间自动总结前一天的对话内容并推送。当用户说'每天X点给我发日报'、'设置每日总结'等时调用。参数：hour(小时0-23)、minute(分钟0-59)。")
    @ToolInvocationLog("schedule_daily_summary")
    public SummaryScheduleToolResult scheduleDailySummary(int hour, int minute) {
        String userId = UserContextHolder.getUserId();

        if (userId == null || userId.isBlank()) {
            return new SummaryScheduleToolResult(false, null, "无法获取用户ID");
        }

        SummaryHandler.ReminderCreateResult result = summaryHandler.createDailySummarySchedule(userId, hour, minute);

        return new SummaryScheduleToolResult(result.success(), result.reminderId(), result.message());
    }

    /**
     * 创建定时周报任务
     */
    @Tool(name = "schedule_weekly_summary",
          description = "创建每周定时对话总结任务（周报）。AI会在每周指定时间自动总结上周的对话内容并推送。当用户说'每周X给我发周报'、'设置每周总结'等时调用。参数：dayOfWeek(1-7，1=周一，7=周日)、hour(小时0-23)、minute(分钟0-59)。")
    @ToolInvocationLog("schedule_weekly_summary")
    public SummaryScheduleToolResult scheduleWeeklySummary(int dayOfWeek, int hour, int minute) {
        String userId = UserContextHolder.getUserId();

        if (userId == null || userId.isBlank()) {
            return new SummaryScheduleToolResult(false, null, "无法获取用户ID");
        }

        SummaryHandler.ReminderCreateResult result = summaryHandler.createWeeklySummarySchedule(userId, dayOfWeek, hour, minute);

        return new SummaryScheduleToolResult(result.success(), result.reminderId(), result.message());
    }

    /**
     * 创建定时月报任务
     */
    @Tool(name = "schedule_monthly_summary",
          description = "创建每月定时对话总结任务（月报）。AI会在每月指定日期自动总结上月的对话内容并推送。当用户说'每月X号给我发月报'、'设置每月总结'等时调用。参数：dayOfMonth(每月的第几天，1-28)、hour(小时0-23)、minute(分钟0-59)。")
    @ToolInvocationLog("schedule_monthly_summary")
    public SummaryScheduleToolResult scheduleMonthlySummary(int dayOfMonth, int hour, int minute) {
        String userId = UserContextHolder.getUserId();

        if (userId == null || userId.isBlank()) {
            return new SummaryScheduleToolResult(false, null, "无法获取用户ID");
        }

        SummaryHandler.ReminderCreateResult result = summaryHandler.createMonthlySummarySchedule(userId, dayOfMonth, hour, minute);

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

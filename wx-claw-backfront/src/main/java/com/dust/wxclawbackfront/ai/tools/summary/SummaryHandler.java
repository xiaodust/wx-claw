package com.dust.wxclawbackfront.ai.tools.summary;

import com.dust.wxclawbackfront.ai.chat.PlainTextLlmService;
import com.dust.wxclawbackfront.ai.dao.entity.AiMessage;
import com.dust.wxclawbackfront.ai.dao.repository.AiMessageRepository;
import com.dust.wxclawbackfront.ai.service.ConversationCompressor;
import com.dust.wxclawbackfront.ai.tools.reminder.ReminderHandler;
import com.dust.wxclawbackfront.ilink.outbound.ILinkMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * 对话总结业务处理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryHandler {

    private final AiMessageRepository messageRepository;
    private final ConversationCompressor compressor;
    private final PlainTextLlmService plainTextLlmService;
    private final ILinkMessageSender messageSender;
    private final ReminderHandler reminderHandler;

    /**
     * 即时总结 - 日报
     */
    public SummaryResult generateDailySummaryNow(String userId) {
        return generateSummaryNow(userId, "DAILY");
    }

    /**
     * 即时总结 - 周报
     */
    public SummaryResult generateWeeklySummaryNow(String userId) {
        return generateSummaryNow(userId, "WEEKLY");
    }

    /**
     * 即时总结 - 月报
     */
    public SummaryResult generateMonthlySummaryNow(String userId) {
        return generateSummaryNow(userId, "MONTHLY");
    }

    /**
     * 即时总结 - 自定义时间范围（小时数）
     */
    public SummaryResult generateCustomSummaryNow(String userId, int hours) {
        if (userId == null || userId.isBlank()) {
            return new SummaryResult(false, "无法获取用户信息");
        }
        if (hours <= 0 || hours > 720) { // 最多30天
            return new SummaryResult(false, "时间范围必须在1-720小时之间");
        }

        try {
            Calendar cal = Calendar.getInstance();
            Date endTime = cal.getTime();
            cal.add(Calendar.HOUR_OF_DAY, -hours);
            Date startTime = cal.getTime();

            return executeSummary(userId, "CUSTOM", startTime, endTime, "过去" + hours + "小时");
        } catch (Exception e) {
            log.error("即时总结失败: userId={}, hours={}, error={}", userId, hours, e.getMessage(), e);
            return new SummaryResult(false, "总结失败：" + e.getMessage());
        }
    }

    /**
     * 即时总结核心逻辑
     */
    private SummaryResult generateSummaryNow(String userId, String summaryType) {
        if (userId == null || userId.isBlank()) {
            return new SummaryResult(false, "无法获取用户信息");
        }

        try {
            Date[] timeRange = calculateTimeRange(summaryType);
            Date startTime = timeRange[0];
            Date endTime = timeRange[1];

            String periodDesc = getPeriodDesc(summaryType);
            return executeSummary(userId, summaryType, startTime, endTime, periodDesc);
        } catch (Exception e) {
            log.error("即时总结失败: userId={}, type={}, error={}", userId, summaryType, e.getMessage(), e);
            return new SummaryResult(false, "总结失败：" + e.getMessage());
        }
    }

    /**
     * 执行总结的核心方法
     */
    private SummaryResult executeSummary(String userId, String summaryType, Date startTime, Date endTime, String periodDesc) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String timeRange = String.format("%s 至 %s", sdf.format(startTime), sdf.format(endTime));

        log.info("执行即时对话总结: userId={}, type={}, range=[{}, {}]",
                userId, summaryType, startTime, endTime);

        // 查询时间范围内的消息
        List<AiMessage> messages = messageRepository.findByUsernameAndTimeRange(userId, startTime, endTime);

        if (messages == null || messages.isEmpty()) {
            String reply = String.format("【%s总结】\n\n时间范围：%s\n\n在此期间暂无对话记录。",
                    periodDesc, timeRange);
            return new SummaryResult(true, reply);
        }

        log.info("查询到 {} 条消息，开始压缩", messages.size());

        // 压缩对话历史
        String compressedHistory = compressor.compressWithSummary(messages);

        // 构建总结提示词
        String summaryPrompt = buildSummaryPrompt(summaryType, startTime, endTime, compressedHistory);

        // 调用纯文本 LLM 生成总结
        String summary = plainTextLlmService.chat(summaryPrompt);

        if (summary == null || summary.isBlank()) {
            return new SummaryResult(false, "AI生成总结失败");
        }

        // 构建最终回复
        String finalReply = String.format("【%s总结】\n\n时间范围：%s\n\n%s",
                periodDesc, timeRange, summary);

        log.info("即时对话总结完成: userId={}, type={}, messageCount={}",
                userId, summaryType, messages.size());

        return new SummaryResult(true, finalReply);
    }

    /**
     * 创建定时总结任务
     */
    public ReminderCreateResult createDailySummarySchedule(String userId, int hour, int minute) {
        ReminderHandler.ReminderCreateResult result = reminderHandler.createDailySummary(userId, hour, minute);
        return new ReminderCreateResult(result.success(), result.reminderId(), result.message());
    }

    public ReminderCreateResult createWeeklySummarySchedule(String userId, int dayOfWeek, int hour, int minute) {
        ReminderHandler.ReminderCreateResult result = reminderHandler.createWeeklySummary(userId, dayOfWeek, hour, minute);
        return new ReminderCreateResult(result.success(), result.reminderId(), result.message());
    }

    public ReminderCreateResult createMonthlySummarySchedule(String userId, int dayOfMonth, int hour, int minute) {
        ReminderHandler.ReminderCreateResult result = reminderHandler.createMonthlySummary(userId, dayOfMonth, hour, minute);
        return new ReminderCreateResult(result.success(), result.reminderId(), result.message());
    }

    private Date[] calculateTimeRange(String summaryType) {
        Calendar cal = Calendar.getInstance();
        Date endTime = cal.getTime();

        switch (summaryType) {
            case "DAILY":
                // 昨天 00:00:00 到今天 00:00:00
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                endTime = cal.getTime();
                cal.add(Calendar.DAY_OF_MONTH, -1);
                break;

            case "WEEKLY":
                // 上周一 00:00:00 到本周一 00:00:00
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
                endTime = cal.getTime();
                cal.add(Calendar.WEEK_OF_YEAR, -1);
                break;

            case "MONTHLY":
                // 上月1号 00:00:00 到本月1号 00:00:00
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                cal.set(Calendar.DAY_OF_MONTH, 1);
                endTime = cal.getTime();
                cal.add(Calendar.MONTH, -1);
                break;

            default:
                throw new IllegalArgumentException("Unknown summary type: " + summaryType);
        }

        Date startTime = cal.getTime();
        return new Date[]{startTime, endTime};
    }

    private String buildSummaryPrompt(String summaryType, Date startTime, Date endTime, String history) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String periodDesc = getPeriodDesc(summaryType);
        String timeRange = String.format("%s 至 %s", sdf.format(startTime), sdf.format(endTime));

        return String.format(
                "你是一个专业的对话总结助手。请基于以下用户与AI的对话记录，生成一份简洁的%s总结报告。\n\n" +
                "时间范围：%s\n\n" +
                "对话记录：\n%s\n\n" +
                "请按以下结构输出总结：\n" +
                "1. 核心话题（列出用户主要关注的话题，3-5个）\n" +
                "2. 重要事项（用户提到的待办事项、重要决策等）\n" +
                "3. 知识要点（对话中涉及的关键知识点或信息）\n" +
                "4. 建议与展望（基于对话内容，给出下一步建议）\n\n" +
                "要求：\n" +
                "- 简洁明了，每条不超过一行\n" +
                "- 突出重点，不必面面俱到\n" +
                "- 使用清晰的分点格式\n" +
                "- 总字数控制在500字以内",
                periodDesc, timeRange, history
        );
    }

    private String getPeriodDesc(String summaryType) {
        return switch (summaryType) {
            case "DAILY" -> "日报";
            case "WEEKLY" -> "周报";
            case "MONTHLY" -> "月报";
            case "CUSTOM" -> "自定义";
            default -> "总结";
        };
    }

    /**
     * 总结结果
     */
    public record SummaryResult(boolean success, String message) {}

    /**
     * 定时总结创建结果
     */
    public record ReminderCreateResult(boolean success, Long reminderId, String message) {}
}

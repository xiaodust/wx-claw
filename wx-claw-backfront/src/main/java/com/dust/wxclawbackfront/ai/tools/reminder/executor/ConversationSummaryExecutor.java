package com.dust.wxclawbackfront.ai.tools.reminder.executor;

import com.dust.wxclawbackfront.ai.chat.ChatHandler;
import com.dust.wxclawbackfront.ai.chat.PlainTextLlmService;
import com.dust.wxclawbackfront.ai.dao.entity.AiMessage;
import com.dust.wxclawbackfront.ai.dao.repository.AiMessageRepository;
import com.dust.wxclawbackfront.ai.service.ConversationCompressor;
import com.dust.wxclawbackfront.ai.tools.reminder.ReminderTask;
import com.dust.wxclawbackfront.ilnk.outbound.ILinkMessageSender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 对话总结执行器
 * 定时根据用户对话历史生成日报/周报/月报
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationSummaryExecutor implements TaskActionExecutor {

    private final AiMessageRepository messageRepository;
    private final ConversationCompressor compressor;
    private final PlainTextLlmService plainTextLlmService;
    private final ILinkMessageSender messageSender;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean execute(ReminderTask task) {
        try {
            String actionParams = task.getActionParams();
            if (actionParams == null || actionParams.isBlank()) {
                log.error("对话总结任务参数为空: taskId={}", task.getId());
                return false;
            }

            // 解析参数
            JsonNode params = objectMapper.readTree(actionParams);
            String summaryType = params.get("summaryType").asText(); // DAILY, WEEKLY, MONTHLY

            // 动态计算时间范围
            Date[] timeRange = calculateTimeRange(summaryType);
            Date startTime = timeRange[0];
            Date endTime = timeRange[1];

            log.info("执行对话总结任务: taskId={}, userId={}, type={}, range=[{}, {}]",
                    task.getId(), task.getUserId(), summaryType, startTime, endTime);

            // 查询时间范围内的消息
            List<AiMessage> messages = messageRepository.findByUsernameAndTimeRange(
                    task.getUserId(), startTime, endTime);

            if (messages == null || messages.isEmpty()) {
                String reply = buildEmptyReply(summaryType, startTime, endTime);
                messageSender.sendText(task.getUserId(), reply);
                log.info("时间范围内无对话记录: taskId={}, userId={}", task.getId(), task.getUserId());
                return true;
            }

            log.info("查询到 {} 条消息，开始压缩", messages.size());

            // 压缩对话历史（采用两阶段压缩：分段摘要 + 汇总）
            String compressedHistory = compressor.compressWithSummary(messages);

            // 构建总结提示词
            String summaryPrompt = buildSummaryPrompt(summaryType, startTime, endTime, compressedHistory);

            // 调用纯文本 LLM 生成总结
            String summary = plainTextLlmService.chat(summaryPrompt);

            if (summary == null || summary.isBlank()) {
                log.error("AI生成总结为空: taskId={}", task.getId());
                return false;
            }

            // 发送总结
            String finalReply = buildFinalReply(summaryType, startTime, endTime, summary);
            messageSender.sendText(task.getUserId(), finalReply);

            log.info("对话总结任务完成: taskId={}, userId={}, messageCount={}",
                    task.getId(), task.getUserId(), messages.size());
            return true;

        } catch (Exception e) {
            log.error("对话总结任务执行异常: taskId={}, error={}", task.getId(), e.getMessage(), e);
            return false;
        }
    }

    private Date[] calculateTimeRange(String summaryType) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        Date endTime = cal.getTime();

        switch (summaryType) {
            case "DAILY":
                // 昨天 00:00:00 到今天 00:00:00
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                cal.set(java.util.Calendar.MINUTE, 0);
                cal.set(java.util.Calendar.SECOND, 0);
                cal.set(java.util.Calendar.MILLISECOND, 0);
                endTime = cal.getTime();
                cal.add(java.util.Calendar.DAY_OF_MONTH, -1);
                break;

            case "WEEKLY":
                // 上周一 00:00:00 到本周一 00:00:00
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                cal.set(java.util.Calendar.MINUTE, 0);
                cal.set(java.util.Calendar.SECOND, 0);
                cal.set(java.util.Calendar.MILLISECOND, 0);
                cal.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY);
                endTime = cal.getTime();
                cal.add(java.util.Calendar.WEEK_OF_YEAR, -1);
                break;

            case "MONTHLY":
                // 上月1号 00:00:00 到本月1号 00:00:00
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                cal.set(java.util.Calendar.MINUTE, 0);
                cal.set(java.util.Calendar.SECOND, 0);
                cal.set(java.util.Calendar.MILLISECOND, 0);
                cal.set(java.util.Calendar.DAY_OF_MONTH, 1);
                endTime = cal.getTime();
                cal.add(java.util.Calendar.MONTH, -1);
                break;

            default:
                throw new IllegalArgumentException("Unknown summary type: " + summaryType);
        }

        Date startTime = cal.getTime();
        return new Date[]{startTime, endTime};
    }

    @Override
    public String getActionType() {
        return "CONVERSATION_SUMMARY";
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

    private String buildEmptyReply(String summaryType, Date startTime, Date endTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String periodDesc = getPeriodDesc(summaryType);
        String timeRange = String.format("%s 至 %s", sdf.format(startTime), sdf.format(endTime));

        return String.format("【%s总结】\n\n时间范围：%s\n\n在此期间暂无对话记录。",
                periodDesc, timeRange);
    }

    private String buildFinalReply(String summaryType, Date startTime, Date endTime, String summary) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String periodDesc = getPeriodDesc(summaryType);
        String timeRange = String.format("%s 至 %s", sdf.format(startTime), sdf.format(endTime));

        return String.format("【%s总结】\n\n时间范围：%s\n\n%s",
                periodDesc, timeRange, summary);
    }

    private String getPeriodDesc(String summaryType) {
        return switch (summaryType) {
            case "DAILY" -> "日报";
            case "WEEKLY" -> "周报";
            case "MONTHLY" -> "月报";
            default -> "总结";
        };
    }
}

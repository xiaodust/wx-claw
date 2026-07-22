package com.dust.wxclawbackfront.bot.agent.tools.reminder;

/**
 * 提醒参数校验工具类
 * 封装通用的参数校验逻辑，避免重复代码
 */
public class ReminderValidator {

    private ReminderValidator() {
    }

    /**
     * 校验用户ID
     */
    public static String validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return "用户ID为空";
        }
        return null;
    }

    /**
     * 校验提醒内容
     */
    public static String validateReminderText(String reminderText) {
        if (reminderText == null || reminderText.isBlank()) {
            return "提醒内容为空";
        }
        return null;
    }

    /**
     * 校验搜索关键词
     */
    public static String validateQuery(String query) {
        if (query == null || query.isBlank()) {
            return "搜索关键词为空";
        }
        return null;
    }

    /**
     * 校验AI提示词
     */
    public static String validatePrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "AI提示词为空";
        }
        return null;
    }

    /**
     * 校验地点
     */
    public static String validateLocation(String location) {
        if (location == null || location.isBlank()) {
            return "地点为空";
        }
        return null;
    }

    /**
     * 校验收件人邮箱
     */
    public static String validateEmailTo(String to) {
        if (to == null || to.isBlank()) {
            return "收件人邮箱为空";
        }
        return null;
    }

    /**
     * 校验邮件主题
     */
    public static String validateEmailSubject(String subject) {
        if (subject == null || subject.isBlank()) {
            return "邮件主题为空";
        }
        return null;
    }

    /**
     * 校验邮件内容
     */
    public static String validateEmailContent(String content) {
        if (content == null || content.isBlank()) {
            return "邮件内容为空";
        }
        return null;
    }

    /**
     * 校验延迟时间（分钟）
     */
    public static String validateDelayMinutes(int delayMinutes, int maxDelayDays) {
        if (delayMinutes <= 0) {
            return "延迟时间必须大于0分钟";
        }
        if (delayMinutes > maxDelayDays * 24 * 60) {
            return "延迟时间不能超过" + maxDelayDays + "天";
        }
        return null;
    }

    /**
     * 校验小时和分钟
     */
    public static String validateHourAndMinute(int hour, int minute) {
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return "时间格式错误（小时0-23，分钟0-59）";
        }
        return null;
    }

    /**
     * 校验星期几
     */
    public static String validateDayOfWeek(int dayOfWeek) {
        if (dayOfWeek < 1 || dayOfWeek > 7) {
            return "星期数错误（1-7，1=周一，7=周日）";
        }
        return null;
    }

    /**
     * 校验每月日期
     */
    public static String validateDayOfMonth(int dayOfMonth) {
        if (dayOfMonth < 1 || dayOfMonth > 31) {
            return "日期错误（1-31）";
        }
        return null;
    }

    /**
     * 校验每月日期（用于月报，限制在1-28）
     */
    public static String validateDayOfMonthForSummary(int dayOfMonth) {
        if (dayOfMonth < 1 || dayOfMonth > 28) {
            return "每月日期必须在1-28之间（避免月份差异）";
        }
        return null;
    }

    /**
     * 创建校验失败结果
     */
    public static ReminderHandler.ReminderCreateResult createErrorResult(String errorMessage) {
        return new ReminderHandler.ReminderCreateResult(false, null, errorMessage);
    }
}

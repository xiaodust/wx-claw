package com.dust.wxclawbackfront.bot.agent.tools.reminder;

import com.dust.wxclawbackfront.bot.dao.entity.ReminderTask;
import com.dust.wxclawbackfront.bot.dao.repository.ReminderTaskRepository;
import com.dust.wxclawbackfront.bot.scheduler.DynamicTaskSchedulerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 提醒任务业务处理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderHandler {

    private final ReminderTaskRepository repository;
    private final DynamicTaskSchedulerService schedulerService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${wxclaw.ai.time.zone:Asia/Shanghai}")
    private String timeZone;

    @Value("${wxclaw.reminder.max-delay-days:30}")
    private int maxDelayDays;

    /**
     * 创建一次性延迟提醒
     */
    @Transactional
    public ReminderCreateResult createDelayReminder(String userId, String reminderText, int delayMinutes) {
        String error;
        if ((error = ReminderValidator.validateUserId(userId)) != null) {
            return ReminderValidator.createErrorResult(error);
        }
        if ((error = ReminderValidator.validateReminderText(reminderText)) != null) {
            return ReminderValidator.createErrorResult(error);
        }
        if ((error = ReminderValidator.validateDelayMinutes(delayMinutes, maxDelayDays)) != null) {
            return ReminderValidator.createErrorResult(error);
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.of(timeZone));
        LocalDateTime triggerTime = now.plusMinutes(delayMinutes);

        ReminderTask task = ReminderTaskBuilder.builder()
                .userId(userId)
                .reminderText(reminderText)
                .triggerTime(triggerTime)
                .taskType("ONE_TIME")
                .actionType("REMINDER")
                .build();

        ReminderTask saved = repository.save(task);
        log.info("创建延迟提醒成功: userId={}, reminderId={}, triggerTime={}, text={}", 
                userId, saved.getId(), triggerTime, reminderText);

        // 注册到调度器
        schedulerService.scheduleOnceTask(saved);

        return new ReminderCreateResult(true, saved.getId(), 
                String.format("好的，我会在 %d 分钟后（%s）提醒你：%s", 
                        delayMinutes, formatTime(triggerTime), reminderText));
    }

    /**
     * 创建一次性延迟网络搜索推送
     */
    @Transactional
    public ReminderCreateResult createDelayWebSearch(String userId, String query, String freshness, int count, int delayMinutes) {
        String error;
        if ((error = ReminderValidator.validateUserId(userId)) != null) {
            return ReminderValidator.createErrorResult(error);
        }
        if ((error = ReminderValidator.validateQuery(query)) != null) {
            return ReminderValidator.createErrorResult(error);
        }
        if ((error = ReminderValidator.validateDelayMinutes(delayMinutes, maxDelayDays)) != null) {
            return ReminderValidator.createErrorResult(error);
        }

        try {
            LocalDateTime now = LocalDateTime.now(ZoneId.of(timeZone));
            LocalDateTime triggerTime = now.plusMinutes(delayMinutes);

            ReminderTask task = ReminderTaskBuilder.builder()
                    .userId(userId)
                    .reminderText("搜索：" + query)
                    .triggerTime(triggerTime)
                    .taskType("ONE_TIME")
                    .actionType("WEB_SEARCH_PUSH")
                    .actionParams(ReminderTaskBuilder.createParams("query", query, "freshness", freshness == null || freshness.isBlank() ? "noLimit" : freshness, "count", count))
                    .build();

            ReminderTask saved = repository.save(task);
            log.info("创建延迟搜索任务成功: userId={}, taskId={}, query={}, triggerTime={}", 
                    userId, saved.getId(), query, triggerTime);

            // 注册到调度器
            schedulerService.scheduleOnceTask(saved);

            return new ReminderCreateResult(true, saved.getId(), 
                    String.format("好的，我会在 %d 分钟后（%s）为你搜索「%s」并推送结果", 
                            delayMinutes, formatTime(triggerTime), query));
        } catch (Exception e) {
            log.error("创建延迟搜索任务失败", e);
            return new ReminderCreateResult(false, null, "创建失败：" + e.getMessage());
        }
    }

    /**
     * 创建一次性延迟 AI 聊天
     */
    @Transactional
    public ReminderCreateResult createDelayAiChat(String userId, String prompt, int delayMinutes) {
        String error;
        if ((error = ReminderValidator.validateUserId(userId)) != null) {
            return ReminderValidator.createErrorResult(error);
        }
        if ((error = ReminderValidator.validatePrompt(prompt)) != null) {
            return ReminderValidator.createErrorResult(error);
        }
        if ((error = ReminderValidator.validateDelayMinutes(delayMinutes, maxDelayDays)) != null) {
            return ReminderValidator.createErrorResult(error);
        }

        try {
            LocalDateTime now = LocalDateTime.now(ZoneId.of(timeZone));
            LocalDateTime triggerTime = now.plusMinutes(delayMinutes);

            ReminderTask task = ReminderTaskBuilder.builder()
                    .userId(userId)
                    .reminderText("AI：" + (prompt.length() > 20 ? prompt.substring(0, 20) + "..." : prompt))
                    .triggerTime(triggerTime)
                    .taskType("ONE_TIME")
                    .actionType("AI_CHAT")
                    .actionParams(ReminderTaskBuilder.createParams("prompt", prompt))
                    .build();

            ReminderTask saved = repository.save(task);
            log.info("创建延迟AI任务成功: userId={}, taskId={}, prompt={}, triggerTime={}", 
                    userId, saved.getId(), prompt, triggerTime);

            // 注册到调度器
            schedulerService.scheduleOnceTask(saved);

            return new ReminderCreateResult(true, saved.getId(), 
                    String.format("好的，我会在 %d 分钟后（%s）自动生成内容并发送给你", 
                            delayMinutes, formatTime(triggerTime)));
        } catch (Exception e) {
            log.error("创建延迟AI任务失败", e);
            return new ReminderCreateResult(false, null, "创建失败：" + e.getMessage());
        }
    }

    /**
     * 创建每天定时提醒
     */
    @Transactional
    public ReminderCreateResult createDailyReminder(String userId, String reminderText, int hour, int minute) {
        String error;
        if ((error = ReminderValidator.validateUserId(userId)) != null) {
            return ReminderValidator.createErrorResult(error);
        }
        if ((error = ReminderValidator.validateReminderText(reminderText)) != null) {
            return ReminderValidator.createErrorResult(error);
        }
        if ((error = ReminderValidator.validateHourAndMinute(hour, minute)) != null) {
            return ReminderValidator.createErrorResult(error);
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.of(timeZone));
        LocalDateTime triggerTime = now.toLocalDate().atTime(hour, minute);
        
        // 如果今天的时间已过，设为明天
        if (triggerTime.isBefore(now) || triggerTime.isEqual(now)) {
            triggerTime = triggerTime.plusDays(1);
        }

        ReminderTask task = ReminderTaskBuilder.builder()
                .userId(userId)
                .reminderText(reminderText)
                .triggerTime(triggerTime)
                .taskType("DAILY")
                .actionType("REMINDER")
                .cronExpression(String.format("0 %d %d * * *", minute, hour))
                .build();

        ReminderTask saved = repository.save(task);
        log.info("创建每日提醒成功: userId={}, reminderId={}, cron={}, text={}", 
                userId, saved.getId(), saved.getCronExpression(), reminderText);

        // 注册到调度器
        schedulerService.scheduleCronTask(saved);

        return new ReminderCreateResult(true, saved.getId(), 
                String.format("好的，我会每天 %02d:%02d 提醒你：%s（首次提醒：%s）", 
                        hour, minute, reminderText, formatTime(triggerTime)));
    }

    /**
     * 创建每周定时提醒
     */
    @Transactional
    public ReminderCreateResult createWeeklyReminder(String userId, String reminderText, int dayOfWeek, int hour, int minute) {
        String error;
        if ((error = ReminderValidator.validateUserId(userId)) != null) {
            return ReminderValidator.createErrorResult(error);
        }
        if ((error = ReminderValidator.validateReminderText(reminderText)) != null) {
            return ReminderValidator.createErrorResult(error);
        }
        if ((error = ReminderValidator.validateDayOfWeek(dayOfWeek)) != null) {
            return ReminderValidator.createErrorResult(error);
        }
        if ((error = ReminderValidator.validateHourAndMinute(hour, minute)) != null) {
            return ReminderValidator.createErrorResult(error);
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.of(timeZone));
        DayOfWeek targetDay = DayOfWeek.of(dayOfWeek);
        
        // 计算下一个指定星期几的日期
        LocalDateTime triggerTime = now.with(TemporalAdjusters.nextOrSame(targetDay))
                .toLocalDate()
                .atTime(hour, minute);
        
        // 如果是今天但时间已过，或刚好是现在，则设为下周同一天
        if (triggerTime.isBefore(now) || triggerTime.isEqual(now)) {
            triggerTime = triggerTime.plusWeeks(1);
        }

        ReminderTask task = ReminderTaskBuilder.builder()
                .userId(userId)
                .reminderText(reminderText)
                .triggerTime(triggerTime)
                .taskType("WEEKLY")
                .actionType("REMINDER")
                .cronExpression(String.format("0 %d %d * * %s", minute, hour, getDayOfWeekCron(dayOfWeek)))
                .build();

        ReminderTask saved = repository.save(task);
        log.info("创建每周提醒成功: userId={}, reminderId={}, cron={}, text={}", 
                userId, saved.getId(), saved.getCronExpression(), reminderText);

        // 注册到调度器
        schedulerService.scheduleCronTask(saved);

        String dayName = getDayOfWeekName(dayOfWeek);
        return new ReminderCreateResult(true, saved.getId(), 
                String.format("好的，我会每周%s %02d:%02d 提醒你：%s（首次提醒：%s）", 
                        dayName, hour, minute, reminderText, formatTime(triggerTime)));
    }

    /**
     * 创建每月定时提醒
     */
    @Transactional
    public ReminderCreateResult createMonthlyReminder(String userId, String reminderText, int dayOfMonth, int hour, int minute) {
        String error;
        if ((error = ReminderValidator.validateUserId(userId)) != null) {
            return ReminderValidator.createErrorResult(error);
        }
        if ((error = ReminderValidator.validateReminderText(reminderText)) != null) {
            return ReminderValidator.createErrorResult(error);
        }
        if ((error = ReminderValidator.validateDayOfMonth(dayOfMonth)) != null) {
            return ReminderValidator.createErrorResult(error);
        }
        if ((error = ReminderValidator.validateHourAndMinute(hour, minute)) != null) {
            return ReminderValidator.createErrorResult(error);
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.of(timeZone));
        LocalDateTime triggerTime = calculateNextMonthlyTrigger(now, dayOfMonth, hour, minute);

        ReminderTask task = ReminderTaskBuilder.builder()
                .userId(userId)
                .reminderText(reminderText)
                .triggerTime(triggerTime)
                .taskType("MONTHLY")
                .actionType("REMINDER")
                .cronExpression(String.format("0 %d %d %d * *", minute, hour, dayOfMonth))
                .build();

        ReminderTask saved = repository.save(task);
        log.info("创建每月提醒成功: userId={}, reminderId={}, cron={}, text={}", 
                userId, saved.getId(), saved.getCronExpression(), reminderText);

        // 注册到调度器
        schedulerService.scheduleCronTask(saved);

        return new ReminderCreateResult(true, saved.getId(), 
                String.format("好的，我会每月%d号 %02d:%02d 提醒你：%s（首次提醒：%s）", 
                        dayOfMonth, hour, minute, reminderText, formatTime(triggerTime)));
    }

    /**
     * 计算周期任务的下次触发时间
     */
    public LocalDateTime calculateNextTriggerTime(ReminderTask task) {
        LocalDateTime current = task.getTriggerTime();
        String taskType = task.getTaskType();
        
        LocalDateTime now = LocalDateTime.now(ZoneId.of(timeZone));
        
        switch (taskType) {
            case "DAILY":
                // 加一天
                LocalDateTime next = current.plusDays(1);
                // 确保不会回到过去
                while (next.isBefore(now)) {
                    next = next.plusDays(1);
                }
                return next;
                
            case "WEEKLY":
                // 加一周
                next = current.plusWeeks(1);
                while (next.isBefore(now)) {
                    next = next.plusWeeks(1);
                }
                return next;
                
            case "MONTHLY":
                // 加一个月，保持日期和时间
                int day = current.getDayOfMonth();
                int hour = current.getHour();
                int minute = current.getMinute();
                return calculateNextMonthlyTrigger(current.plusMonths(1), day, hour, minute);
                
            default:
                return null;
        }
    }

    /**
     * 计算下一个每月触发时间（处理月末日期）
     */
    private LocalDateTime calculateNextMonthlyTrigger(LocalDateTime fromTime, int dayOfMonth, int hour, int minute) {
        LocalDateTime result = fromTime.toLocalDate().withDayOfMonth(1).atTime(hour, minute);
        
        // 如果目标日期超过当月最大天数，使用当月最后一天
        int maxDay = result.toLocalDate().lengthOfMonth();
        int actualDay = Math.min(dayOfMonth, maxDay);
        result = result.withDayOfMonth(actualDay);
        
        // 如果时间已过，尝试下个月
        if (result.isBefore(fromTime) || result.isEqual(fromTime)) {
            result = result.plusMonths(1).withDayOfMonth(1);
            maxDay = result.toLocalDate().lengthOfMonth();
            actualDay = Math.min(dayOfMonth, maxDay);
            result = result.withDayOfMonth(actualDay);
        }
        
        return result;
    }

    /**
     * 查询用户的待执行提醒列表
     */
    public List<ReminderTask> listPendingReminders(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        return repository.findByUserIdAndStatusOrderByTriggerTimeAsc(userId, "PENDING");
    }

    /**
     * 取消提醒
     */
    @Transactional
    public ReminderCancelResult cancelReminder(String userId, Long reminderId) {
        if (userId == null || userId.isBlank()) {
            return new ReminderCancelResult(false, "用户ID为空");
        }
        if (reminderId == null) {
            return new ReminderCancelResult(false, "提醒ID为空");
        }

        return repository.findById(reminderId)
                .map(task -> {
                    if (!task.getUserId().equals(userId)) {
                        return new ReminderCancelResult(false, "无权取消此提醒");
                    }
                    if (!"PENDING".equals(task.getStatus())) {
                        return new ReminderCancelResult(false, "提醒已执行或已取消");
                    }
                    
                    // 取消数据库中的任务
                    task.setStatus("CANCELLED");
                    task.setExecutedAt(LocalDateTime.now(ZoneId.of(timeZone)));
                    repository.save(task);
                    
                    // 取消调度器中的任务
                    schedulerService.cancelTask(reminderId);
                    
                    log.info("取消提醒成功: userId={}, reminderId={}", userId, reminderId);
                    return new ReminderCancelResult(true, "已取消提醒：" + task.getReminderText());
                })
                .orElse(new ReminderCancelResult(false, "提醒不存在"));
    }

    /**
     * 格式化时间显示
     */
    private String formatTime(LocalDateTime time) {
        int hour = time.getHour();
        int minute = time.getMinute();
        return String.format("%d月%d日 %02d:%02d", time.getMonthValue(), time.getDayOfMonth(), hour, minute);
    }

    /**
     * 获取星期几的中文名称
     */
    private String getDayOfWeekName(int dayOfWeek) {
        return switch (dayOfWeek) {
            case 1 -> "一";
            case 2 -> "二";
            case 3 -> "三";
            case 4 -> "四";
            case 5 -> "五";
            case 6 -> "六";
            case 7 -> "日";
            default -> String.valueOf(dayOfWeek);
        };
    }

    /**
     * 获取星期几的 Cron 表达式格式
     * 1=周一 对应 MON, 7=周日 对应 SUN
     */
    private String getDayOfWeekCron(int dayOfWeek) {
        return switch (dayOfWeek) {
            case 1 -> "MON";
            case 2 -> "TUE";
            case 3 -> "WED";
            case 4 -> "THU";
            case 5 -> "FRI";
            case 6 -> "SAT";
            case 7 -> "SUN";
            default -> "*";
        };
    }

    /**
     * 创建提醒结果
     */
    @Transactional
    public ReminderCreateResult createDailyWeatherPush(String userId, String location, int hour, int minute, boolean includeForecast) {
        String error;
        if ((error = ReminderValidator.validateUserId(userId)) != null) {
            return ReminderValidator.createErrorResult(error);
        }
        if ((error = ReminderValidator.validateLocation(location)) != null) {
            return ReminderValidator.createErrorResult(error);
        }
        if ((error = ReminderValidator.validateHourAndMinute(hour, minute)) != null) {
            return ReminderValidator.createErrorResult(error);
        }

        try {
            LocalDateTime now = LocalDateTime.now(ZoneId.of(timeZone));
            LocalDateTime triggerTime = now.toLocalDate().atTime(hour, minute);
            
            if (triggerTime.isBefore(now) || triggerTime.isEqual(now)) {
                triggerTime = triggerTime.plusDays(1);
            }

            ReminderTask task = ReminderTaskBuilder.builder()
                    .userId(userId)
                    .reminderText("每日天气推送：" + location)
                    .triggerTime(triggerTime)
                    .taskType("DAILY")
                    .actionType("WEATHER_PUSH")
                    .actionParams(ReminderTaskBuilder.createParams("location", location, "includeForecast", includeForecast))
                    .cronExpression(String.format("0 %d %d * * *", minute, hour))
                    .build();

            ReminderTask saved = repository.save(task);
            log.info("创建每日天气推送成功: userId={}, taskId={}, location={}, cron={}", 
                    userId, saved.getId(), location, saved.getCronExpression());

            // 注册到调度器
            schedulerService.scheduleCronTask(saved);

            return new ReminderCreateResult(true, saved.getId(), 
                    String.format("好的，我会每天 %02d:%02d 为你推送%s的天气（首次推送：%s）", 
                            hour, minute, location, formatTime(triggerTime)));
        } catch (Exception e) {
            log.error("创建天气推送任务失败", e);
            return new ReminderCreateResult(false, null, "创建失败：" + e.getMessage());
        }
    }

    /**
     * 创建定时邮件发送（每天）
     */
    @Transactional
    public ReminderCreateResult createDailyEmail(String userId, String to, String subject, String content, int hour, int minute, boolean isHtml) {
        String error;
        if ((error = ReminderValidator.validateUserId(userId)) != null) {
            return ReminderValidator.createErrorResult(error);
        }
        if ((error = ReminderValidator.validateEmailTo(to)) != null) {
            return ReminderValidator.createErrorResult(error);
        }
        if ((error = ReminderValidator.validateEmailSubject(subject)) != null) {
            return ReminderValidator.createErrorResult(error);
        }
        if ((error = ReminderValidator.validateEmailContent(content)) != null) {
            return ReminderValidator.createErrorResult(error);
        }
        if ((error = ReminderValidator.validateHourAndMinute(hour, minute)) != null) {
            return ReminderValidator.createErrorResult(error);
        }

        try {
            LocalDateTime now = LocalDateTime.now(ZoneId.of(timeZone));
            LocalDateTime triggerTime = now.toLocalDate().atTime(hour, minute);
            
            if (triggerTime.isBefore(now) || triggerTime.isEqual(now)) {
                triggerTime = triggerTime.plusDays(1);
            }

            ReminderTask task = ReminderTaskBuilder.builder()
                    .userId(userId)
                    .reminderText("每日邮件：" + subject)
                    .triggerTime(triggerTime)
                    .taskType("DAILY")
                    .actionType("EMAIL")
                    .actionParams(ReminderTaskBuilder.createParams("to", to, "subject", subject, "content", content))
                    .cronExpression(String.format("0 %d %d * * *", minute, hour))
                    .build();

            ReminderTask saved = repository.save(task);
            log.info("创建每日邮件任务成功: userId={}, taskId={}, to={}, subject={}, cron={}", 
                    userId, saved.getId(), to, subject, saved.getCronExpression());

            // 注册到调度器
            schedulerService.scheduleCronTask(saved);

            return new ReminderCreateResult(true, saved.getId(), 
                    String.format("好的，我会每天 %02d:%02d 给 %s 发送邮件《%s》（首次发送：%s）", 
                            hour, minute, to, subject, formatTime(triggerTime)));
        } catch (Exception e) {
            log.error("创建邮件任务失败", e);
            return new ReminderCreateResult(false, null, "创建失败：" + e.getMessage());
        }
    }

    /**
     * 创建定时网络搜索推送（每天）
     */
    @Transactional
    public ReminderCreateResult createDailyWebSearch(String userId, String query, String freshness, int count, int hour, int minute) {
        String error;
        if ((error = ReminderValidator.validateUserId(userId)) != null) {
            return ReminderValidator.createErrorResult(error);
        }
        if ((error = ReminderValidator.validateQuery(query)) != null) {
            return ReminderValidator.createErrorResult(error);
        }
        if ((error = ReminderValidator.validateHourAndMinute(hour, minute)) != null) {
            return ReminderValidator.createErrorResult(error);
        }

        try {
            LocalDateTime now = LocalDateTime.now(ZoneId.of(timeZone));
            LocalDateTime triggerTime = now.toLocalDate().atTime(hour, minute);
            
            if (triggerTime.isBefore(now) || triggerTime.isEqual(now)) {
                triggerTime = triggerTime.plusDays(1);
            }

            ReminderTask task = ReminderTaskBuilder.builder()
                    .userId(userId)
                    .reminderText("每日搜索：" + query)
                    .triggerTime(triggerTime)
                    .taskType("DAILY")
                    .actionType("WEB_SEARCH_PUSH")
                    .actionParams(ReminderTaskBuilder.createParams("query", query, "freshness", freshness == null || freshness.isBlank() ? "noLimit" : freshness, "count", count))
                    .cronExpression(String.format("0 %d %d * * *", minute, hour))
                    .build();

            ReminderTask saved = repository.save(task);
            log.info("创建每日搜索任务成功: userId={}, taskId={}, query={}, cron={}", 
                    userId, saved.getId(), query, saved.getCronExpression());

            // 注册到调度器
            schedulerService.scheduleCronTask(saved);

            return new ReminderCreateResult(true, saved.getId(), 
                    String.format("好的，我会每天 %02d:%02d 为你搜索「%s」并推送结果（首次推送：%s）", 
                            hour, minute, query, formatTime(triggerTime)));
        } catch (Exception e) {
            log.error("创建搜索任务失败", e);
            return new ReminderCreateResult(false, null, "创建失败：" + e.getMessage());
        }
    }

    /**
     * 创建定时 AI 聊天（每天）
     */
    @Transactional
    public ReminderCreateResult createDailyAiChat(String userId, String prompt, int hour, int minute) {
        String error;
        if ((error = ReminderValidator.validateUserId(userId)) != null) {
            return ReminderValidator.createErrorResult(error);
        }
        if ((error = ReminderValidator.validatePrompt(prompt)) != null) {
            return ReminderValidator.createErrorResult(error);
        }
        if ((error = ReminderValidator.validateHourAndMinute(hour, minute)) != null) {
            return ReminderValidator.createErrorResult(error);
        }

        try {
            LocalDateTime now = LocalDateTime.now(ZoneId.of(timeZone));
            LocalDateTime triggerTime = now.toLocalDate().atTime(hour, minute);
            
            if (triggerTime.isBefore(now) || triggerTime.isEqual(now)) {
                triggerTime = triggerTime.plusDays(1);
            }

            ReminderTask task = ReminderTaskBuilder.builder()
                    .userId(userId)
                    .reminderText("每日AI：" + (prompt.length() > 20 ? prompt.substring(0, 20) + "..." : prompt))
                    .triggerTime(triggerTime)
                    .taskType("DAILY")
                    .actionType("AI_CHAT")
                    .actionParams(ReminderTaskBuilder.createParams("prompt", prompt))
                    .cronExpression(String.format("0 %d %d * * *", minute, hour))
                    .build();

            ReminderTask saved = repository.save(task);
            log.info("创建每日AI任务成功: userId={}, taskId={}, prompt={}, cron={}", 
                    userId, saved.getId(), prompt, saved.getCronExpression());

            // 注册到调度器
            schedulerService.scheduleCronTask(saved);

            return new ReminderCreateResult(true, saved.getId(), 
                    String.format("好的，我会每天 %02d:%02d 自动生成内容并发送给你（首次：%s）", 
                            hour, minute, formatTime(triggerTime)));
        } catch (Exception e) {
            log.error("创建AI任务失败", e);
            return new ReminderCreateResult(false, null, "创建失败：" + e.getMessage());
        }
    }

    /**
     * 创建每日对话总结（日报）
     */
    @Transactional
    public ReminderCreateResult createDailySummary(String userId, int hour, int minute) {
        String error;
        if ((error = ReminderValidator.validateUserId(userId)) != null) {
            return ReminderValidator.createErrorResult(error);
        }
        if ((error = ReminderValidator.validateHourAndMinute(hour, minute)) != null) {
            return ReminderValidator.createErrorResult(error);
        }

        try {
            LocalDateTime now = LocalDateTime.now(ZoneId.of(timeZone));
            LocalDateTime triggerTime = now.toLocalDate().atTime(hour, minute);
            
            if (triggerTime.isBefore(now) || triggerTime.isEqual(now)) {
                triggerTime = triggerTime.plusDays(1);
            }

            ReminderTask task = ReminderTaskBuilder.builder()
                    .userId(userId)
                    .reminderText("每日对话总结（日报）")
                    .triggerTime(triggerTime)
                    .taskType("DAILY")
                    .actionType("CONVERSATION_SUMMARY")
                    .actionParams(ReminderTaskBuilder.createParams("summaryType", "DAILY", "startTime", 0L, "endTime", 0L))
                    .cronExpression(String.format("0 %d %d * * *", minute, hour))
                    .build();

            ReminderTask saved = repository.save(task);
            schedulerService.scheduleCronTask(saved);

            return new ReminderCreateResult(true, saved.getId(), 
                    String.format("好的，我会每天 %02d:%02d 为你生成对话日报", hour, minute));

        } catch (Exception e) {
            log.error("创建每日总结任务失败: userId={}, error={}", userId, e.getMessage(), e);
            return new ReminderCreateResult(false, null, "创建失败：" + e.getMessage());
        }
    }

    /**
     * 创建每周对话总结（周报）
     */
    @Transactional
    public ReminderCreateResult createWeeklySummary(String userId, int dayOfWeek, int hour, int minute) {
        String error;
        if ((error = ReminderValidator.validateUserId(userId)) != null) {
            return ReminderValidator.createErrorResult(error);
        }
        if ((error = ReminderValidator.validateDayOfWeek(dayOfWeek)) != null) {
            return ReminderValidator.createErrorResult(error);
        }
        if ((error = ReminderValidator.validateHourAndMinute(hour, minute)) != null) {
            return ReminderValidator.createErrorResult(error);
        }

        try {
            LocalDateTime now = LocalDateTime.now(ZoneId.of(timeZone));
            LocalDateTime triggerTime = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(dayOfWeek)))
                    .with(LocalTime.of(hour, minute));
            
            if (triggerTime.isBefore(now) || triggerTime.isEqual(now)) {
                triggerTime = triggerTime.plusWeeks(1);
            }

            ReminderTask task = ReminderTaskBuilder.builder()
                    .userId(userId)
                    .reminderText("每周对话总结（周报）")
                    .triggerTime(triggerTime)
                    .taskType("WEEKLY")
                    .actionType("CONVERSATION_SUMMARY")
                    .actionParams(ReminderTaskBuilder.createParams("summaryType", "WEEKLY", "startTime", 0L, "endTime", 0L))
                    .cronExpression(String.format("0 %d %d * * %s", minute, hour, getDayOfWeekCron(dayOfWeek)))
                    .build();

            ReminderTask saved = repository.save(task);
            schedulerService.scheduleCronTask(saved);

            return new ReminderCreateResult(true, saved.getId(), 
                    String.format("好的，我会每周%s %02d:%02d 为你生成对话周报", 
                            getDayOfWeekName(dayOfWeek), hour, minute));

        } catch (Exception e) {
            log.error("创建每周总结任务失败: userId={}, error={}", userId, e.getMessage(), e);
            return new ReminderCreateResult(false, null, "创建失败：" + e.getMessage());
        }
    }

    /**
     * 创建每月对话总结（月报）
     */
    @Transactional
    public ReminderCreateResult createMonthlySummary(String userId, int dayOfMonth, int hour, int minute) {
        String error;
        if ((error = ReminderValidator.validateUserId(userId)) != null) {
            return ReminderValidator.createErrorResult(error);
        }
        if ((error = ReminderValidator.validateDayOfMonthForSummary(dayOfMonth)) != null) {
            return ReminderValidator.createErrorResult(error);
        }
        if ((error = ReminderValidator.validateHourAndMinute(hour, minute)) != null) {
            return ReminderValidator.createErrorResult(error);
        }

        try {
            LocalDateTime now = LocalDateTime.now(ZoneId.of(timeZone));
            LocalDateTime triggerTime = now.toLocalDate().withDayOfMonth(dayOfMonth).atTime(hour, minute);
            
            if (triggerTime.isBefore(now) || triggerTime.isEqual(now)) {
                triggerTime = triggerTime.plusMonths(1);
            }

            ReminderTask task = ReminderTaskBuilder.builder()
                    .userId(userId)
                    .reminderText("每月对话总结（月报）")
                    .triggerTime(triggerTime)
                    .taskType("MONTHLY")
                    .actionType("CONVERSATION_SUMMARY")
                    .actionParams(ReminderTaskBuilder.createParams("summaryType", "MONTHLY", "startTime", 0L, "endTime", 0L))
                    .cronExpression(String.format("0 %d %d %d * *", minute, hour, dayOfMonth))
                    .build();

            ReminderTask saved = repository.save(task);
            schedulerService.scheduleCronTask(saved);

            return new ReminderCreateResult(true, saved.getId(), 
                    String.format("好的，我会每月%d号 %02d:%02d 为你生成对话月报", dayOfMonth, hour, minute));

        } catch (Exception e) {
            log.error("创建每月总结任务失败: userId={}, error={}", userId, e.getMessage(), e);
            return new ReminderCreateResult(false, null, "创建失败：" + e.getMessage());
        }
    }

    /**
     * 创建提醒结果
     */
    public record ReminderCreateResult(boolean success, Long reminderId, String message) {}

    /**
     * 取消提醒结果
     */
    public record ReminderCancelResult(boolean success, String message) {}
}

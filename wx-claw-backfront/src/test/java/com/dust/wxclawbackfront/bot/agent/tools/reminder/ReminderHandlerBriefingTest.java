package com.dust.wxclawbackfront.bot.agent.tools.reminder;

import com.dust.wxclawbackfront.bot.dao.entity.ReminderTask;
import com.dust.wxclawbackfront.bot.dao.repository.ReminderTaskRepository;
import com.dust.wxclawbackfront.bot.scheduler.DynamicTaskSchedulerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReminderHandlerBriefingTest {

    private final ReminderTaskRepository repository = mock(ReminderTaskRepository.class);
    private final DynamicTaskSchedulerService schedulerService = mock(DynamicTaskSchedulerService.class);
    private final ReminderHandler handler = new ReminderHandler(repository, schedulerService);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(handler, "timeZone", "Asia/Shanghai");
        when(repository.save(any(ReminderTask.class))).thenAnswer(invocation -> {
            ReminderTask task = invocation.getArgument(0);
            task.setId(100L);
            return task;
        });
    }

    @Test
    void persistsUserSelectedWeeklySchedule() {
        ReminderHandler.ReminderCreateResult result = handler.createScheduledBriefingEmail(
                "user", "user@example.com", "行业简报", "杭州", "人工智能行业资讯",
                5, "WEEKLY", 3, 1, 18, 30, true);

        assertThat(result.success()).isTrue();
        ArgumentCaptor<ReminderTask> taskCaptor = ArgumentCaptor.forClass(ReminderTask.class);
        verify(repository).save(taskCaptor.capture());
        ReminderTask task = taskCaptor.getValue();
        assertThat(task.getTaskType()).isEqualTo("WEEKLY");
        assertThat(task.getActionType()).isEqualTo("SCHEDULED_BRIEFING_EMAIL");
        assertThat(task.getCronExpression()).isEqualTo("0 30 18 * * WED");
        verify(schedulerService).scheduleCronTask(task);
    }
}

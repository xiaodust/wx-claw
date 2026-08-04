package com.dust.wxclawbackfront.bot.scheduler;

import com.dust.wxclawbackfront.bot.agent.tools.reminder.executor.TaskActionExecutor;
import com.dust.wxclawbackfront.bot.agent.tools.reminder.executor.TaskActionExecutorRegistry;
import com.dust.wxclawbackfront.bot.dao.entity.ReminderTask;
import com.dust.wxclawbackfront.bot.dao.repository.ReminderTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicTaskSchedulerServiceRetryTest {

    private TaskScheduler taskScheduler;
    private ReminderTaskRepository repository;
    private TaskActionExecutorRegistry executorRegistry;
    private DynamicTaskSchedulerService service;

    @BeforeEach
    void setUp() {
        taskScheduler = mock(TaskScheduler.class);
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class)))
                .thenReturn(mock(ScheduledFuture.class));
        repository = mock(ReminderTaskRepository.class);
        executorRegistry = mock(TaskActionExecutorRegistry.class);
        service = new DynamicTaskSchedulerService(taskScheduler, repository, executorRegistry);
        ReflectionTestUtils.setField(service, "timeZone", "Asia/Shanghai");
        ReflectionTestUtils.setField(service, "retryFailed", true);
        ReflectionTestUtils.setField(service, "maxRetryCount", 3);
        ReflectionTestUtils.setField(service, "retryBackoffSeconds", 30L);
    }

    @Test
    void retriesOneTimeTaskWithBackoffWhenRetryable() {
        ReminderTask task = oneTimeTask(0);
        TaskActionExecutor executor = mock(TaskActionExecutor.class);
        when(executor.execute(task)).thenReturn(false);
        when(repository.findByTenantIdAndId("tenant-a", 1L)).thenReturn(Optional.of(task));
        when(executorRegistry.getExecutor("REMINDER")).thenReturn(executor);

        service.executeTask(task);

        assertThat(task.getStatus()).isEqualTo("PENDING");
        assertThat(task.getRetryCount()).isEqualTo(1);
        assertThat(task.getTriggerTime()).isAfter(LocalDateTime.now().minusSeconds(1));
        assertThat(task.getFailureReason()).isNotBlank();
        verify(repository).save(task);
        verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void marksFailedAfterRetriesExhausted() {
        ReminderTask task = oneTimeTask(3);
        TaskActionExecutor executor = mock(TaskActionExecutor.class);
        when(executor.execute(task)).thenReturn(false);
        when(repository.findByTenantIdAndId("tenant-a", 1L)).thenReturn(Optional.of(task));
        when(executorRegistry.getExecutor("REMINDER")).thenReturn(executor);

        service.executeTask(task);

        assertThat(task.getStatus()).isEqualTo("FAILED");
        assertThat(task.getRetryCount()).isEqualTo(4);
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void marksFailedImmediatelyForNonRetryableError() {
        ReminderTask task = oneTimeTask(0);
        TaskActionExecutor executor = mock(TaskActionExecutor.class);
        when(executor.execute(task)).thenThrow(new IllegalArgumentException("参数不合法"));
        when(repository.findByTenantIdAndId("tenant-a", 1L)).thenReturn(Optional.of(task));
        when(executorRegistry.getExecutor("REMINDER")).thenReturn(executor);

        service.executeTask(task);

        assertThat(task.getStatus()).isEqualTo("FAILED");
        assertThat(task.getRetryCount()).isEqualTo(1);
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void keepsCronTaskPendingAfterFailure() {
        ReminderTask task = oneTimeTask(0);
        task.setTaskType("DAILY");
        TaskActionExecutor executor = mock(TaskActionExecutor.class);
        when(executor.execute(task)).thenReturn(false);
        when(repository.findByTenantIdAndId("tenant-a", 1L)).thenReturn(Optional.of(task));
        when(executorRegistry.getExecutor("REMINDER")).thenReturn(executor);

        service.executeTask(task);

        assertThat(task.getStatus()).isEqualTo("PENDING");
        assertThat(task.getRetryCount()).isEqualTo(1);
        verify(repository, times(1)).save(task);
    }

    private ReminderTask oneTimeTask(int retryCount) {
        ReminderTask task = new ReminderTask();
        task.setId(1L);
        task.setTenantId("tenant-a");
        task.setUserId("user-1");
        task.setInternalUserId("user-1");
        task.setChannel("ILINK");
        task.setBotId("bot-1");
        task.setChannelUserId("wx-user-1");
        task.setTaskType("ONE_TIME");
        task.setActionType("REMINDER");
        task.setStatus("PENDING");
        task.setRetryCount(retryCount);
        task.setTriggerTime(LocalDateTime.now().minusSeconds(1));
        return task;
    }
}

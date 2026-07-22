package com.dust.wxclawbackfront.bot.agent.model;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 任务计划
 */
@Data
@Builder
public class TaskPlan {

    private List<TaskStep> steps;

    public static TaskPlan chat() {
        TaskStep step = TaskStep.builder()
                .stepNumber(1)
                .toolName("chat")
                .params(Collections.emptyMap())
                .description("普通对话")
                .build();
        return TaskPlan.builder()
                .steps(Collections.singletonList(step))
                .build();
    }

    public int getStepCount() {
        return steps == null ? 0 : steps.size();
    }
}

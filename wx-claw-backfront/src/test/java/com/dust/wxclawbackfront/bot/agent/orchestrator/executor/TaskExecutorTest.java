package com.dust.wxclawbackfront.bot.agent.orchestrator.executor;

import com.dust.wxclawbackfront.bot.agent.model.AgentContext;
import com.dust.wxclawbackfront.bot.agent.model.TaskPlan;
import com.dust.wxclawbackfront.bot.agent.model.TaskResult;
import com.dust.wxclawbackfront.bot.agent.model.TaskStep;
import com.dust.wxclawbackfront.bot.agent.orchestrator.tool.ToolHandler;
import com.dust.wxclawbackfront.bot.agent.orchestrator.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class TaskExecutorTest {
    @Test
    void skipsDependentStepAfterFailureAndReportsFailure() {
        AtomicBoolean dependentCalled = new AtomicBoolean();
        ToolHandler first = handler("first", (step, context) -> TaskResult.failure("boom", 2));
        ToolHandler second = handler("second", (step, context) -> {
            dependentCalled.set(true);
            return TaskResult.success("unexpected", 1);
        });
        TaskExecutor executor = new TaskExecutor(new ToolRegistry(List.of(first, second)));
        TaskPlan plan = TaskPlan.builder().steps(List.of(
                TaskStep.builder().stepNumber(1).toolName("first").params(Map.of()).build(),
                TaskStep.builder().stepNumber(2).toolName("second").params(Map.of()).dependsOn(1).build())).build();

        List<TaskResult> results = executor.execute(plan, AgentContext.builder().build());
        TaskResult merged = executor.mergeResults(results);

        assertThat(dependentCalled).isFalse();
        assertThat(merged.isSuccess()).isFalse();
        assertThat(merged.getErrorMessage()).contains("boom", "已跳过");
    }

    private ToolHandler handler(String name, HandlerCall call) {
        return new ToolHandler() {
            @Override public String getName() { return name; }
            @Override public TaskResult execute(TaskStep step, AgentContext context) { return call.execute(step, context); }
        };
    }

    private interface HandlerCall { TaskResult execute(TaskStep step, AgentContext context); }
}

package com.dust.wxclawbackfront.bot.agent.orchestrator.executor;

import com.dust.wxclawbackfront.bot.agent.model.AgentContext;
import com.dust.wxclawbackfront.bot.agent.model.TaskPlan;
import com.dust.wxclawbackfront.bot.agent.model.TaskResult;
import com.dust.wxclawbackfront.bot.agent.model.TaskStep;
import com.dust.wxclawbackfront.bot.agent.orchestrator.tool.ToolHandler;
import com.dust.wxclawbackfront.bot.agent.orchestrator.tool.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务执行器
 * 按步骤执行任务计划，处理步骤间依赖
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskExecutor {

    private final ToolRegistry toolRegistry;

    /**
     * 执行任务计划
     */
    public List<TaskResult> execute(TaskPlan plan, AgentContext context) {
        List<TaskResult> results = new ArrayList<>();
        Map<Integer, TaskResult> completedSteps = new HashMap<>();

        for (TaskStep step : plan.getSteps()) {
            TaskResult blocked = dependencyFailure(step, completedSteps);
            if (blocked != null) {
                results.add(blocked);
                completedSteps.put(step.getStepNumber(), blocked);
                continue;
            }
            // 注入依赖步骤的结果
            injectDependencyResult(step, completedSteps);

            // 执行步骤
            TaskResult result = executeStep(step, context);
            results.add(result);
            completedSteps.put(step.getStepNumber(), result);

            // 步骤失败时记录日志
            if (!result.isSuccess()) {
                log.warn("步骤 {} 执行失败: {}", step.getStepNumber(), result.getErrorMessage());
            }
        }

        return results;
    }

    private TaskResult dependencyFailure(TaskStep step, Map<Integer, TaskResult> completedSteps) {
        if (step.getDependsOn() == null) return null;
        TaskResult dependency = completedSteps.get(step.getDependsOn());
        if (dependency == null) {
            return TaskResult.failure("依赖步骤 " + step.getDependsOn() + " 未执行", 0);
        }
        if (!dependency.isSuccess()) {
            return TaskResult.failure("依赖步骤 " + step.getDependsOn() + " 执行失败，已跳过", 0);
        }
        return null;
    }

    /**
     * 注入依赖步骤的结果
     */
    private void injectDependencyResult(TaskStep step, Map<Integer, TaskResult> completedSteps) {
        if (step.getDependsOn() == null) {
            return;
        }

        TaskResult dependencyResult = completedSteps.get(step.getDependsOn());
        if (dependencyResult == null) {
            log.warn("依赖步骤 {} 未找到结果", step.getDependsOn());
            return;
        }

        step.setParams(step.getParams() == null ? new HashMap<>() : new HashMap<>(step.getParams()));

        // 将前一步的文本结果作为当前步骤的输入
        if (dependencyResult.getTextResult() != null && !dependencyResult.getTextResult().isBlank()) {
            String paramName = resolveInputParamName(step.getToolName());
            step.getParams().put(paramName, dependencyResult.getTextResult());
        }

        // 传递媒体数据
        if (dependencyResult.hasMedia()) {
            step.getParams().put("previousMediaBytes", dependencyResult.getMediaBytes());
            step.getParams().put("previousMediaType", dependencyResult.getMediaType());
        }
    }

    /**
     * 执行单个步骤
     */
    private TaskResult executeStep(TaskStep step, AgentContext context) {
        ToolHandler handler = toolRegistry.findHandler(step.getToolName())
                .orElse(null);

        if (handler == null) {
            log.error("未找到工具处理器: {}", step.getToolName());
            return TaskResult.failure("未找到工具: " + step.getToolName(), 0);
        }

        log.info("执行步骤 {}: tool={}", step.getStepNumber(), step.getToolName());
        return handler.execute(step, context);
    }

    /**
     * 合并多个步骤的结果
     */
    public TaskResult mergeResults(List<TaskResult> results) {
        if (results == null || results.isEmpty()) {
            return TaskResult.failure("无执行结果", 0);
        }

        if (results.size() == 1) {
            return results.get(0);
        }

        // 收集失败信息
        List<String> errors = new ArrayList<>();
        for (TaskResult result : results) {
            if (!result.isSuccess()) {
                errors.add(result.getErrorMessage());
            }
        }

        if (errors.size() == results.size()) {
            return TaskResult.failure(String.join("; ", errors), totalExecutionTime(results));
        }

        // 合并文本结果（只取成功步骤的文本）
        StringBuilder textResult = new StringBuilder();
        long totalTime = 0;
        for (TaskResult result : results) {
            totalTime += result.getExecutionTimeMs();
            if (result.isSuccess() && result.getTextResult() != null) {
                if (textResult.length() > 0) {
                    textResult.append("\n");
                }
                textResult.append(result.getTextResult());
            }
        }

        // 优先返回最后一个有媒体的结果
        TaskResult lastMediaResult = null;
        for (int i = results.size() - 1; i >= 0; i--) {
            if (results.get(i).hasMedia()) {
                lastMediaResult = results.get(i);
                break;
            }
        }

        String errorSuffix = errors.isEmpty() ? "" : "\n[部分步骤失败: " + String.join("; ", errors) + "]";
        if (lastMediaResult != null) {
            return TaskResult.successWithMedia(
                    textResult + errorSuffix,
                    lastMediaResult.getMediaBytes(),
                    lastMediaResult.getMediaType(),
                    lastMediaResult.getMediaFileName(),
                    totalTime);
        }

        // 如果有失败步骤，在文本结果中追加错误信息
        String finalText = textResult.toString();
        finalText += errorSuffix;

        return TaskResult.success(finalText, totalTime);
    }

    private long totalExecutionTime(List<TaskResult> results) {
        return results.stream().mapToLong(TaskResult::getExecutionTimeMs).sum();
    }

    /**
     * 根据工具名称确定输入参数名
     */
    private String resolveInputParamName(String toolName) {
        return switch (toolName) {
            case "voice_synthesize" -> "text";
            case "image_generate" -> "prompt";
            default -> "input";
        };
    }
}

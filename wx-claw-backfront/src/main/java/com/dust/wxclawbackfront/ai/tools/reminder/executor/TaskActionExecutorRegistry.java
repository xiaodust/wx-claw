package com.dust.wxclawbackfront.ai.tools.reminder.executor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 任务执行器注册表
 */
@Slf4j
@Component
public class TaskActionExecutorRegistry {
    
    private final Map<String, TaskActionExecutor> executors;
    
    public TaskActionExecutorRegistry(List<TaskActionExecutor> executorList) {
        this.executors = executorList.stream()
                .collect(Collectors.toMap(
                        TaskActionExecutor::getActionType,
                        Function.identity()
                ));
        
        log.info("TaskActionExecutorRegistry initialized with {} executors: {}", 
                executors.size(), executors.keySet());
    }
    
    /**
     * 根据动作类型获取执行器
     */
    public TaskActionExecutor getExecutor(String actionType) {
        return executors.get(actionType);
    }
    
    /**
     * 判断是否支持某个动作类型
     */
    public boolean supports(String actionType) {
        return executors.containsKey(actionType);
    }
}

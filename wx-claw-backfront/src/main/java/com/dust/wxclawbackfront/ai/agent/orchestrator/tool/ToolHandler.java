package com.dust.wxclawbackfront.ai.agent.orchestrator.tool;

import com.dust.wxclawbackfront.ai.agent.model.AgentContext;
import com.dust.wxclawbackfront.ai.agent.model.TaskResult;
import com.dust.wxclawbackfront.ai.agent.model.TaskStep;

/**
 * Agent 工具处理器接口
 */
public interface ToolHandler {

    /**
     * 获取工具名称（唯一标识）
     */
    String getName();

    /**
     * 执行工具
     */
    TaskResult execute(TaskStep step, AgentContext context);
}

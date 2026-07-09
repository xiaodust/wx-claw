package com.dust.wxclawbackfront.ai.agent.detector;

import com.dust.wxclawbackfront.ai.agent.ToolRequirement;
import com.dust.wxclawbackfront.ai.tools.shared.AiToolInvocationStore;

import java.util.List;
import java.util.Set;

/**
 * 工具需求检测器接口
 * 每个工具域实现一个检测器，负责判断是否需要该工具以及如何补调
 */
public interface ToolRequirementDetector {

    /**
     * 检测用户消息是否需要此工具
     * @param userMessage 用户原始消息
     * @param calledTools 已调用的工具名称集合
     * @return 如果需要此工具返回 ToolRequirement，否则返回 null
     */
    ToolRequirement detect(String userMessage, Set<String> calledTools);

    /**
     * 直接补调工具
     * @param requirement 工具需求
     * @return 工具调用结果（可能为 null 表示不支持直接补调）
     */
    AiToolInvocationStore.Invocation fillTool(ToolRequirement requirement);

    /**
     * 获取此检测器负责的工具名称
     * @return 工具名称
     */
    String getToolName();
}

package com.dust.wxclawbackfront.ai.agent.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 任务步骤
 */
@Data
@Builder
public class TaskStep {

    /**
     * 步骤序号（从 1 开始）
     */
    private int stepNumber;

    /**
     * 工具名称
     */
    private String toolName;

    /**
     * 工具参数
     */
    private Map<String, Object> params;

    /**
     * 依赖的前置步骤序号（null 表示无依赖）
     */
    private Integer dependsOn;

    /**
     * 步骤描述（用于 trace）
     */
    private String description;

    /**
     * 是否有前置依赖
     */
    public boolean hasDependency() {
        return dependsOn != null && dependsOn > 0;
    }
}

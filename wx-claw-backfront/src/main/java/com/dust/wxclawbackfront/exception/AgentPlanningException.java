package com.dust.wxclawbackfront.exception;

/**
 * Agent 规划相关异常
 */
public class AgentPlanningException extends WxClawException {

    public AgentPlanningException(String message) {
        super("AGENT_PLANNING_ERROR", message);
    }

    public AgentPlanningException(String message, Throwable cause) {
        super("AGENT_PLANNING_ERROR", message, cause);
    }
}
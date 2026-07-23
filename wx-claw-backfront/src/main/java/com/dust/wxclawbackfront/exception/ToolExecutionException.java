package com.dust.wxclawbackfront.exception;

/**
 * 工具执行相关异常
 */
public class ToolExecutionException extends WxClawException {
    private final String toolName;

    public ToolExecutionException(String toolName, String message) {
        super("TOOL_EXECUTION_ERROR", message);
        this.toolName = toolName;
    }

    public ToolExecutionException(String toolName, String message, Throwable cause) {
        super("TOOL_EXECUTION_ERROR", message, cause);
        this.toolName = toolName;
    }

    public String getToolName() {
        return toolName;
    }
}
package com.dust.wxclawbackfront.bot.agent.mcp.jobhelper;

public class JobHelperMcpException extends RuntimeException {
    private final int statusCode;
    private final String code;
    private final String requestId;
    private final String retryAfter;

    public JobHelperMcpException(int statusCode, String code, String message,
                                 String requestId, String retryAfter, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.code = code;
        this.requestId = requestId;
        this.retryAfter = retryAfter;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getCode() {
        return code;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getRetryAfter() {
        return retryAfter;
    }
}

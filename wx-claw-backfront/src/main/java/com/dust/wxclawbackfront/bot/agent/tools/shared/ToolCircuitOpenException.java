package com.dust.wxclawbackfront.bot.agent.tools.shared;

/**
 * 工具被跨请求熔断拦截时抛出。
 * 该异常会作为工具调用错误回传给模型，提示模型改用其他工具或稍后重试。
 */
public class ToolCircuitOpenException extends IllegalStateException {

    public ToolCircuitOpenException(String message) {
        super(message);
    }
}

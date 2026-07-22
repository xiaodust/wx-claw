package com.dust.wxclawbackfront.bot.agent.tools.shared;

/**
 * AI工具提供者接口
 * 实现此接口的类会自动被注册到工具链中
 */
public interface AiToolProvider {

    /**
     * 获取工具实例（通常是 this）
     */
    Object getTool();

    /**
     * 工具优先级（数字越小越先注册）
     */
    default int getOrder() {
        return 100;
    }
}

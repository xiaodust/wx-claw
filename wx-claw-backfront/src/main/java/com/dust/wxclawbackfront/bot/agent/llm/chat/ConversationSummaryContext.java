package com.dust.wxclawbackfront.bot.agent.llm.chat;

/**
 * 当前请求携带的会话摘要（窗口外早期对话），由 Agent 入口设置、聊天处理器读取。
 */
public final class ConversationSummaryContext {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private ConversationSummaryContext() {
    }

    public static void set(String summary) {
        HOLDER.set(summary);
    }

    public static String get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}

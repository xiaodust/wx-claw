package com.dust.wxclawbackfront.bot.agent.llm.chat;

/**
 * 当前请求携带的向量记忆召回结果（相关历史对话片段），由 Agent 入口设置、聊天处理器读取。
 */
public final class MemoryRecallContext {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private MemoryRecallContext() {
    }

    public static void set(String recallText) {
        HOLDER.set(recallText);
    }

    public static String get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}

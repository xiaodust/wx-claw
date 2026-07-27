package com.dust.wxclawbackfront.observability.llm;

public final class InvocationTraceContextHolder {
    private static final ThreadLocal<InvocationTraceContext> CONTEXT = new ThreadLocal<>();

    private InvocationTraceContextHolder() {
    }

    public static InvocationTraceContext getNullable() {
        return CONTEXT.get();
    }

    public static void set(InvocationTraceContext context) {
        if (context == null) {
            CONTEXT.remove();
        } else {
            CONTEXT.set(context);
        }
    }

    public static void clear() {
        CONTEXT.remove();
    }
}

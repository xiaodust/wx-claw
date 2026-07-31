package com.dust.wxclawbackfront.tenancy;

/**
 * 当前线程的租户上下文容器。
 *
 * <p>Web 请求、iLink 消息和定时任务进入业务层前必须设置上下文，结束时必须调用
 * {@link #clear()}。线程池会复用线程，如果遗漏清理，后续任务可能读取到前一个租户，
 * 造成严重的跨租户数据访问。</p>
 */
public final class TenantContextHolder {

    private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void set(TenantContext context) {
        if (context == null || context.tenantId() == null || context.tenantId().isBlank()) {
            throw new MissingTenantContextException("Tenant context must contain a tenantId");
        }
        CONTEXT.set(context);
    }

    /**
     * 返回当前上下文，允许为空。仅适用于装饰器等需要“有则传播”的基础设施代码。
     */
    public static TenantContext getNullable() {
        return CONTEXT.get();
    }

    /**
     * 返回当前上下文；业务代码应使用该方法，缺失上下文时立即失败而不是回退到默认租户。
     */
    public static TenantContext require() {
        TenantContext context = CONTEXT.get();
        if (context == null || context.tenantId() == null || context.tenantId().isBlank()) {
            throw new MissingTenantContextException("Tenant context is required");
        }
        return context;
    }

    /** 移除 ThreadLocal 值，必须在请求或任务的 finally 块中调用。 */
    public static void clear() {
        CONTEXT.remove();
    }
}

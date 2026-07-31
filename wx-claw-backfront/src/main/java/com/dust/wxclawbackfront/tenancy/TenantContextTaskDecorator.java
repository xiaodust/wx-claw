package com.dust.wxclawbackfront.tenancy;

import com.dust.wxclawbackfront.observability.llm.InvocationTraceContext;
import com.dust.wxclawbackfront.observability.llm.InvocationTraceContextHolder;
import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

/**
 * 将提交线程中的租户上下文和 LLM 调用链上下文复制到异步执行线程。
 *
 * <p>上下文在任务提交时捕获，而不是在任务真正执行时读取，从而保证排队期间即使
 * 原线程已经结束，异步任务仍使用正确的租户身份。</p>
 */
@Component
public class TenantContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // TaskDecorator 在提交线程执行，因此这里捕获的是发起任务者的上下文。
        TenantContext captured = TenantContextHolder.getNullable();
        InvocationTraceContext capturedTrace = InvocationTraceContextHolder.getNullable();
        return () -> {
            try {
                if (captured != null) {
                    TenantContextHolder.set(captured);
                }
                if (capturedTrace != null) {
                    InvocationTraceContextHolder.set(capturedTrace);
                }
                runnable.run();
            } finally {
                // 执行器线程会被复用，必须无条件清理，防止上下文泄漏给下一个任务。
                InvocationTraceContextHolder.clear();
                TenantContextHolder.clear();
            }
        };
    }
}

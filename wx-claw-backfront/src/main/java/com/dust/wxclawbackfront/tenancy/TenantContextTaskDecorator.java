package com.dust.wxclawbackfront.tenancy;

import com.dust.wxclawbackfront.observability.llm.InvocationTraceContext;
import com.dust.wxclawbackfront.observability.llm.InvocationTraceContextHolder;
import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

@Component
public class TenantContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
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
                InvocationTraceContextHolder.clear();
                TenantContextHolder.clear();
            }
        };
    }
}

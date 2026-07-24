package com.dust.wxclawbackfront.tenancy;

import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

@Component
public class TenantContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        TenantContext captured = TenantContextHolder.getNullable();
        return () -> {
            try {
                if (captured != null) {
                    TenantContextHolder.set(captured);
                }
                runnable.run();
            } finally {
                TenantContextHolder.clear();
            }
        };
    }
}

package com.dust.wxclawbackfront.tenancy;

/**
 * 业务代码要求租户隔离但调用链尚未建立租户上下文时抛出。
 * 该异常用于快速暴露上下文传播遗漏，避免查询或写入落到错误租户。
 */
public class MissingTenantContextException extends IllegalStateException {
    public MissingTenantContextException(String message) {
        super(message);
    }
}

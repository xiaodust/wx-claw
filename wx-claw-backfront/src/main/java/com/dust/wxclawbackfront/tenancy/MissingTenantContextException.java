package com.dust.wxclawbackfront.tenancy;

public class MissingTenantContextException extends IllegalStateException {
    public MissingTenantContextException(String message) {
        super(message);
    }
}

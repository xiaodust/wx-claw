package com.dust.wxclawbackfront.tenancy.service;

import org.springframework.http.HttpStatus;

/**
 * 租户注册的业务异常：携带稳定的错误码和 HTTP 状态，由公开控制器映射为结构化错误体。
 */
public class TenantRegistrationException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;

    public TenantRegistrationException(String errorCode, String message, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String errorCode() {
        return errorCode;
    }

    public HttpStatus status() {
        return status;
    }
}

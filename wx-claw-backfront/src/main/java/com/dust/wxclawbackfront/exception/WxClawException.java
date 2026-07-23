package com.dust.wxclawbackfront.exception;

/**
 * WX-Claw 项目基础异常类
 */
public class WxClawException extends RuntimeException {
    private final String code;

    public WxClawException(String code, String message) {
        super(message);
        this.code = code;
    }

    public WxClawException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
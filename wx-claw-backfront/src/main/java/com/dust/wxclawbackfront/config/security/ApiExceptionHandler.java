package com.dust.wxclawbackfront.config.security;

import com.dust.wxclawbackfront.tenancy.MissingTenantContextException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({SecurityException.class, MissingTenantContextException.class})
    public ResponseEntity<Map<String, String>> forbidden(RuntimeException exception) {
        log.warn("API 访问被拒绝: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Forbidden", "message", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> notFound(IllegalArgumentException exception) {
        log.warn("API 参数/资源异常: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Not Found", "message", exception.getMessage()));
    }
}

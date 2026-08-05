package com.dust.wxclawbackfront.bot.agent.tools.shared;

import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 工具调用日志切面
 * 自动记录带有 @ToolInvocationLog 注解的方法的调用参数和结果
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ToolInvocationAspect {

    private final AiToolInvocationStore invocationStore;
    private final AgentToolLoopGuard loopGuard;
    private final ToolCircuitBreaker circuitBreaker;

    @Around("@annotation(toolInvocationLog)")
    public Object logInvocation(ProceedingJoinPoint joinPoint, ToolInvocationLog toolInvocationLog) throws Throwable {
        String toolName = toolInvocationLog.value();
        String args = formatArgs(joinPoint);
        String userKey = resolveUserKey();
        loopGuard.check(toolName, args);

        // 跨请求熔断：打开期间在工具执行前直接拦截，错误回传给模型
        try {
            circuitBreaker.checkAllowed(toolName, userKey);
        } catch (ToolCircuitOpenException ex) {
            invocationStore.add(toolName, args, "熔断拦截: " + ex.getMessage());
            throw ex;
        }

        log.info("AI调用 {}: {}", toolName, truncate(args, 200));

        try {
            Object result = joinPoint.proceed();
            String response = formatResult(result);
            invocationStore.add(toolName, args, response);
            if (circuitBreaker.isFailureResult(result)) {
                circuitBreaker.recordFailure(toolName, userKey);
            } else {
                circuitBreaker.recordSuccess(toolName, userKey);
            }
            return result;
        } catch (Throwable e) {
            circuitBreaker.recordFailure(toolName, userKey);
            String errorMsg = "调用失败: " + e.getMessage();
            invocationStore.add(toolName, args, errorMsg);
            throw e;
        }
    }

    /**
     * 解析用户维度 key（tenantId::botId::userId）；无上下文时返回 null（仅全局熔断）。
     */
    private String resolveUserKey() {
        String userId = UserContextHolder.getUserId();
        if (userId == null || userId.isBlank()) {
            return null;
        }
        TenantContext context = TenantContextHolder.getNullable();
        String tenantId = context == null ? "default" : context.tenantId();
        String botId = context == null ? "default" : context.botId();
        return tenantId + "::" + botId + "::" + userId;
    }

    private String formatArgs(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        if (paramNames == null || paramNames.length == 0) {
            return "无参数";
        }

        return Arrays.stream(paramNames)
                .map(i -> {
                    int index = Arrays.asList(paramNames).indexOf(i);
                    Object value = index < args.length ? args[index] : "null";
                    return i + "=" + String.valueOf(value);
                })
                .collect(Collectors.joining(", "));
    }

    private String formatResult(Object result) {
        if (result == null) {
            return "null";
        }
        String str = result.toString();
        return str;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "null";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}

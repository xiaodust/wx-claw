package com.dust.wxclawbackfront.bot.agent.tools.shared;

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

    @Around("@annotation(toolInvocationLog)")
    public Object logInvocation(ProceedingJoinPoint joinPoint, ToolInvocationLog toolInvocationLog) throws Throwable {
        String toolName = toolInvocationLog.value();
        String args = formatArgs(joinPoint);

        log.info("AI调用 {}: {}", toolName, truncate(args, 200));

        try {
            Object result = joinPoint.proceed();
            String response = formatResult(result);
            invocationStore.add(toolName, args, response);
            return result;
        } catch (Throwable e) {
            String errorMsg = "调用失败: " + e.getMessage();
            invocationStore.add(toolName, args, errorMsg);
            throw e;
        }
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

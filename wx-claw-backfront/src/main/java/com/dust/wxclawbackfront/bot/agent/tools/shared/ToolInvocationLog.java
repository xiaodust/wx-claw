package com.dust.wxclawbackfront.bot.agent.tools.shared;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具调用日志注解
 * 用于自动记录工具调用的参数和结果，避免在每个 @Tool 方法中重复编写日志代码
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolInvocationLog {

    /**
     * 工具名称
     */
    String value();
}

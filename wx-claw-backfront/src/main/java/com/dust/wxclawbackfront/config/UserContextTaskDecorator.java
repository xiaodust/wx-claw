package com.dust.wxclawbackfront.config;

import com.dust.wxclawbackfront.bot.agent.tools.shared.UserContextHolder;
import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

@Component
public class UserContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        String userId = UserContextHolder.getUserId();
        return () -> {
            try {
                if (userId != null) {
                    UserContextHolder.setUserId(userId);
                }
                runnable.run();
            } finally {
                UserContextHolder.clear();
            }
        };
    }
}
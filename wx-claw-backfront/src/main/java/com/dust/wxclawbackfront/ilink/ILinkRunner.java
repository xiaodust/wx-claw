package com.dust.wxclawbackfront.ilink;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

@Component
@ConditionalOnProperty(prefix = "wxclaw.ilink.monitor", name = "enabled", havingValue = "true")
@Order(100)
public class ILinkRunner implements CommandLineRunner {

    private final ILinkBotService botService;

    public ILinkRunner(ILinkBotService botService) {
        this.botService = botService;
    }

    @Override
    public void run(String... args) {
        botService.startAllActiveBots();
    }
}

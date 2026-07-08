package com.dust.wxclawbackfront.ilnk;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "wxclaw.ilink.monitor", name = "enabled", havingValue = "true")
public class ILinkRunner implements CommandLineRunner {

    private final ILinkBotService botService;

    public ILinkRunner(ILinkBotService botService) {
        this.botService = botService;
    }

    @Override
    public void run(String... args) {
        botService.runILinkMonitor();
    }
}

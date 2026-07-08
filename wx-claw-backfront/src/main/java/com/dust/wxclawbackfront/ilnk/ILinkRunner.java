package com.dust.wxclawbackfront.ilnk;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
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

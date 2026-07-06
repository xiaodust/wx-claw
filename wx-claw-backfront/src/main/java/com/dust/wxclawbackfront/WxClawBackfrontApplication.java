package com.dust.wxclawbackfront;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
public class WxClawBackfrontApplication {

    private static final Logger log = LoggerFactory.getLogger(WxClawBackfrontApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(WxClawBackfrontApplication.class, args);
    }

    @Bean
    public CommandLineRunner helloWorldRunner() {
        return args -> log.info("Hello World");
    }

}

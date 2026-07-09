package com.dust.wxclawbackfront;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WxClawBackfrontApplication {

    public static void main(String[] args) {
        SpringApplication.run(WxClawBackfrontApplication.class, args);
    }
}

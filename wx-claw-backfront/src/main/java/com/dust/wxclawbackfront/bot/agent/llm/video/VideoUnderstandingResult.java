package com.dust.wxclawbackfront.bot.agent.llm.video;

import lombok.Getter;

@Getter
public final class VideoUnderstandingResult {

    private final String model;
    private final String requestJson;
    private final String description;
    private final String errorMsg;

    public VideoUnderstandingResult(String model, String requestJson, String description, String errorMsg) {
        this.model = model;
        this.requestJson = requestJson;
        this.description = description;
        this.errorMsg = errorMsg;
    }
}

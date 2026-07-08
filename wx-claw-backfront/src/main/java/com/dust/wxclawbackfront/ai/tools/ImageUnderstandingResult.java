package com.dust.wxclawbackfront.ai.tools;

import lombok.Getter;

@Getter
public final class ImageUnderstandingResult {

    private final String model;
    private final String requestJson;
    private final String description;
    private final String errorMsg;

    public ImageUnderstandingResult(String model, String requestJson, String description, String errorMsg) {
        this.model = model;
        this.requestJson = requestJson;
        this.description = description;
        this.errorMsg = errorMsg;
    }
}

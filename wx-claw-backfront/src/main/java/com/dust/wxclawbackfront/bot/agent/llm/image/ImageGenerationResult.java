package com.dust.wxclawbackfront.bot.agent.llm.image;

import lombok.Getter;

@Getter
public final class ImageGenerationResult {

    private final String model;
    private final String requestJson;
    private final String responseJson;
    private final String revisedPrompt;
    private final String imageUrl;
    private final byte[] imageBytes;
    private final String fileName;
    private final String contentType;
    private final String errorMsg;

    public ImageGenerationResult(String model,
                                 String requestJson,
                                 String responseJson,
                                 String revisedPrompt,
                                 String imageUrl,
                                 byte[] imageBytes,
                                 String fileName,
                                 String contentType,
                                 String errorMsg) {
        this.model = model;
        this.requestJson = requestJson;
        this.responseJson = responseJson;
        this.revisedPrompt = revisedPrompt;
        this.imageUrl = imageUrl;
        this.imageBytes = imageBytes;
        this.fileName = fileName;
        this.contentType = contentType;
        this.errorMsg = errorMsg;
    }
}


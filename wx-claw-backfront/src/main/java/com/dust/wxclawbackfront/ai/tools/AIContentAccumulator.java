package com.dust.wxclawbackfront.ai.tools;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AIContentAccumulator {

    private final StringBuilder content = new StringBuilder();
    private String requestText;
    private String model;
    private String llmRequestJson;

    public void reset() {
        clearContent();
        requestText = null;
        model = null;
        llmRequestJson = null;
    }

    public void clearContent() {
        content.setLength(0);
    }

    public void append(String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        content.append(part);
    }

    public void setFinalContent(String finalContent) {
        clearContent();
        append(finalContent);
    }

    public String getContent() {
        return content.toString();
    }

    public String getRequestText() {
        return requestText;
    }

    public void setRequestText(String requestText) {
        this.requestText = requestText;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getLlmRequestJson() {
        return llmRequestJson;
    }

    public void setLlmRequestJson(String llmRequestJson) {
        this.llmRequestJson = llmRequestJson;
    }
}

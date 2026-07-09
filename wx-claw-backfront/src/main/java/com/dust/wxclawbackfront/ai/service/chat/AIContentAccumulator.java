package com.dust.wxclawbackfront.ai.service.chat;

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
    private String toolName;
    private String toolRequest;
    private String toolResponse;
    private String agentTraceJson;
    private Integer agentRounds;
    private Boolean agentCompleted;

    public void reset() {
        clearContent();
        requestText = null;
        model = null;
        llmRequestJson = null;
        toolName = null;
        toolRequest = null;
        toolResponse = null;
        agentTraceJson = null;
        agentRounds = null;
        agentCompleted = null;
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

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getToolRequest() {
        return toolRequest;
    }

    public void setToolRequest(String toolRequest) {
        this.toolRequest = toolRequest;
    }

    public String getToolResponse() {
        return toolResponse;
    }

    public void setToolResponse(String toolResponse) {
        this.toolResponse = toolResponse;
    }

    public String getAgentTraceJson() {
        return agentTraceJson;
    }

    public void setAgentTraceJson(String agentTraceJson) {
        this.agentTraceJson = agentTraceJson;
    }

    public Integer getAgentRounds() {
        return agentRounds;
    }

    public void setAgentRounds(Integer agentRounds) {
        this.agentRounds = agentRounds;
    }

    public Boolean getAgentCompleted() {
        return agentCompleted;
    }

    public void setAgentCompleted(Boolean agentCompleted) {
        this.agentCompleted = agentCompleted;
    }
}


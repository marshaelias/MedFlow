package com.medflow.agent;

import java.util.Map;

public class AgentResult {
    private boolean success;
    private Map<String, Object> data;
    private String error;
    private String fallbackSuggestion;

    private AgentResult(boolean success, Map<String, Object> data, String error, String fallbackSuggestion) {
        this.success = success;
        this.data = data;
        this.error = error;
        this.fallbackSuggestion = fallbackSuggestion;
    }

    public static AgentResult ok(Map<String, Object> data) {
        return new AgentResult(true, data, null, null);
    }

    public static AgentResult fail(String error, String fallbackSuggestion) {
        return new AgentResult(false, null, error, fallbackSuggestion);
    }

    public boolean isSuccess() { return success; }
    public Map<String, Object> getData() { return data; }
    public String getError() { return error; }
    public String getFallbackSuggestion() { return fallbackSuggestion; }
}

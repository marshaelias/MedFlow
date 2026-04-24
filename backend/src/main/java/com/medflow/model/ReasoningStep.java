package com.medflow.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class ReasoningStep {
    private String stepId;
    private String agent;
    private String action;
    private String reasoning;
    private StepStatus status;
    private Map<String, Object> input;
    private Map<String, Object> output;
    private String fallbackPlan;
    private Instant timestamp;

    public enum StepStatus {
        PENDING, RUNNING, COMPLETED, FAILED, SKIPPED
    }

    public ReasoningStep() {
        this.stepId = UUID.randomUUID().toString();
        this.status = StepStatus.PENDING;
        this.timestamp = Instant.now();
    }

    public ReasoningStep(String agent, String action, String reasoning) {
        this();
        this.agent = agent;
        this.action = action;
        this.reasoning = reasoning;
    }

    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }
    public String getAgent() { return agent; }
    public void setAgent(String agent) { this.agent = agent; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }
    public StepStatus getStatus() { return status; }
    public void setStatus(StepStatus status) { this.status = status; }
    public Map<String, Object> getInput() { return input; }
    public void setInput(Map<String, Object> input) { this.input = input; }
    public Map<String, Object> getOutput() { return output; }
    public void setOutput(Map<String, Object> output) { this.output = output; }
    public String getFallbackPlan() { return fallbackPlan; }
    public void setFallbackPlan(String fallbackPlan) { this.fallbackPlan = fallbackPlan; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}

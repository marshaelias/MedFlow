package com.medflow.model;

import java.time.Instant;
import java.util.*;

public class WorkflowState {
    private String workflowId;
    private String patientId;
    private WorkflowStatus status;
    private String currentStep;
    private List<ReasoningStep> reasoningChain;
    private Map<String, Object> context;
    private Instant createdAt;
    private Instant updatedAt;

    public enum WorkflowStatus {
        IDLE, ANALYZING, AWAITING_DATA, EXECUTING, COMPLETED, COMPLETED_WITH_RECOVERY, FAILED, RECOVERING
    }

    public WorkflowState() {
        this.workflowId = UUID.randomUUID().toString();
        this.status = WorkflowStatus.IDLE;
        this.reasoningChain = new ArrayList<>();
        this.context = new HashMap<>();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void addReasoningStep(ReasoningStep step) {
        reasoningChain.add(step);
        this.updatedAt = Instant.now();
    }

    public String getWorkflowId() { return workflowId; }
    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }
    public WorkflowStatus getStatus() { return status; }
    public void setStatus(WorkflowStatus status) { this.status = status; this.updatedAt = Instant.now(); }
    public String getCurrentStep() { return currentStep; }
    public void setCurrentStep(String currentStep) { this.currentStep = currentStep; this.updatedAt = Instant.now(); }
    public List<ReasoningStep> getReasoningChain() { return reasoningChain; }
    public void setReasoningChain(List<ReasoningStep> reasoningChain) { this.reasoningChain = reasoningChain; }
    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> context) { this.context = context; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

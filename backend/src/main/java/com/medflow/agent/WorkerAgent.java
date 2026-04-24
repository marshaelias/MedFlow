package com.medflow.agent;

import com.medflow.model.ReasoningStep;
import java.util.Map;

public interface WorkerAgent {
    String getName();
    String getDescription();
    AgentResult execute(Map<String, Object> input, ReasoningStep currentStep);
    boolean canHandle(String intent);
}

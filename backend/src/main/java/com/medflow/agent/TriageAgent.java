package com.medflow.agent;

import com.medflow.client.GlmClient;
import com.medflow.model.ReasoningStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class TriageAgent implements WorkerAgent {

    private final GlmClient glmClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public TriageAgent(GlmClient glmClient) {
        this.glmClient = glmClient;
    }

    @Override
    public String getName() { return "TriageAgent"; }

    @Override
    public String getDescription() { return "Analyzes patient symptoms and determines urgency classification"; }

    @Override
    public boolean canHandle(String intent) {
        return intent != null && (intent.contains("triage") || intent.contains("symptom")
                || intent.contains("urgency") || intent.contains("assess"));
    }

    @Override
    public AgentResult execute(Map<String, Object> input, ReasoningStep currentStep) {
        currentStep.setStatus(ReasoningStep.StepStatus.RUNNING);

        try {
            String patientMessage = (String) input.getOrDefault("message", "");
            List<String> symptoms = input.containsKey("symptoms")
                    ? (List<String>) input.get("symptoms")
                    : Collections.emptyList();

            if (patientMessage.isEmpty() && symptoms.isEmpty()) {
                currentStep.setStatus(ReasoningStep.StepStatus.FAILED);
                return AgentResult.fail(
                    "No symptoms or patient message provided for triage",
                    "Ask the patient to describe their symptoms before triage can proceed"
                );
            }

            GlmClient.GlmRequest request = new GlmClient.GlmRequest()
                    .system("""
                        You are a medical triage AI. Analyze the patient's symptoms and determine:
                        1. urgency_level: one of "critical", "high", "medium", "low"
                        2. likely_condition: brief description of likely condition
                        3. recommended_department: which department to route to
                        4. additional_questions: list of follow-up questions to ask the patient
                        5. reasoning: your clinical reasoning step by step

                        Respond in JSON format.
                        """)
                    .user("Patient message: " + patientMessage +
                          (symptoms.isEmpty() ? "" : "\nListed symptoms: " + String.join(", ", symptoms)))
                    .jsonMode();

            GlmClient.GlmResponse response = glmClient.chat(request);
            Map<String, Object> triageData = mapper.readValue(response.getContent(), Map.class);

            currentStep.setOutput(triageData);
            currentStep.setStatus(ReasoningStep.StepStatus.COMPLETED);

            return AgentResult.ok(triageData);

        } catch (Exception e) {
            currentStep.setStatus(ReasoningStep.StepStatus.FAILED);
            currentStep.setFallbackPlan("Use rule-based triage scoring as fallback");
            return fallbackTriage(input, currentStep);
        }
    }

    private AgentResult fallbackTriage(Map<String, Object> input, ReasoningStep currentStep) {
        String message = (String) input.getOrDefault("message", "");
        String urgency = "medium";
        String condition = "General assessment needed";

        List<String> criticalKeywords = List.of("chest pain", "breathing", "unconscious", "severe bleeding", "stroke");
        List<String> highKeywords = List.of("fever", "infection", "fracture", "allergic", "severe headache", "dizziness");

        String lower = message.toLowerCase();
        if (criticalKeywords.stream().anyMatch(lower::contains)) {
            urgency = "critical";
            condition = "Possible emergency - immediate attention required";
        } else if (highKeywords.stream().anyMatch(lower::contains)) {
            urgency = "high";
            condition = "Urgent care may be needed";
        }

        Map<String, Object> data = new HashMap<>();
        data.put("urgency_level", urgency);
        data.put("likely_condition", condition);
        data.put("recommended_department", urgency.equals("critical") ? "Emergency" : "General Practice");
        data.put("reasoning", "Rule-based fallback triage (GLM unavailable)");
        data.put("fallback_used", true);

        return AgentResult.ok(data);
    }
}

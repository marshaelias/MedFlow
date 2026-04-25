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
        if (intent == null) return false;
        String lower = intent.toLowerCase();
        return lower.contains("triage") || lower.contains("symptom") || lower.contains("urgency")
                || lower.contains("assess") || lower.contains("medical") || lower.contains("health")
                || lower.contains("illness") || lower.contains("condition") || lower.contains("diagnosis");
    }

    @Override
    public AgentResult execute(Map<String, Object> input, ReasoningStep currentStep) {
        currentStep.setStatus(ReasoningStep.StepStatus.RUNNING);

        try {
            String patientMessage = (String) input.getOrDefault("message", "");
            @SuppressWarnings("unchecked")
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
                        You are a medical triage AI. Analyze ONLY the symptoms the patient has explicitly mentioned.

                        CRITICAL RULES:
                        - Extract ONLY symptoms the patient explicitly stated. Do NOT add related or assumed symptoms.
                        - If the patient says "cough and fever", extract ONLY "cough" and "fever" — NOT "sore throat" or "fatigue".
                        - Assign severity based ONLY on the patient's own description (e.g., "severe" = high, "mild" = low).

                        Determine:
                        1. symptoms: list of ONLY explicitly mentioned symptoms
                        2. severity_per_symptom: map of each symptom to its severity (high/medium/low)
                        3. urgency_level: one of "critical", "high", "medium", "low"
                        4. likely_condition: brief description based ONLY on stated symptoms
                        5. recommended_department: which department to route to
                        6. reasoning: step by step reasoning (note what was explicitly stated vs inferred)

                        Respond in JSON format.
                        """)
                    .user("Patient message: " + patientMessage +
                          (symptoms.isEmpty() ? "" : "\nListed symptoms: " + String.join(", ", symptoms)))
                    .jsonMode();

            GlmClient.GlmResponse response = glmClient.chat(request);
            @SuppressWarnings("unchecked")
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

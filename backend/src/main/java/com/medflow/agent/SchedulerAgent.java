package com.medflow.agent;

import com.medflow.client.GlmClient;
import com.medflow.model.ReasoningStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SchedulerAgent implements WorkerAgent {

    private final GlmClient glmClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public SchedulerAgent(GlmClient glmClient) {
        this.glmClient = glmClient;
    }

    @Override
    public String getName() { return "SchedulerAgent"; }

    @Override
    public String getDescription() { return "Schedules appointments based on urgency, department, and availability"; }

    @Override
    public boolean canHandle(String intent) {
        if (intent == null) return false;
        String lower = intent.toLowerCase();
        return lower.contains("schedule") || lower.contains("appointment")
                || lower.contains("book") || lower.contains("availability")
                || lower.contains("visit") || lower.contains("consultation");
    }

    @Override
    public AgentResult execute(Map<String, Object> input, ReasoningStep currentStep) {
        currentStep.setStatus(ReasoningStep.StepStatus.RUNNING);

        try {
            String urgency = (String) input.getOrDefault("urgency_level", "medium");
            String department = (String) input.getOrDefault("department",
                    input.getOrDefault("recommended_department", "General Practice"));
            String preferredTime = (String) input.get("preferred_time");
            String patientName = (String) input.getOrDefault("patient_name", "Patient");

            if (department == null || department.isEmpty()) {
                currentStep.setStatus(ReasoningStep.StepStatus.FAILED);
                return AgentResult.fail(
                    "No department specified for scheduling",
                    "Determine department from triage results first, then retry scheduling"
                );
            }

            GlmClient.GlmRequest request = new GlmClient.GlmRequest()
                    .system("""
                        You are a medical scheduling AI. Given the patient's urgency, department, and constraints,
                        determine the best appointment option.

                        CRITICAL RULES:
                        - Respect ALL patient-stated constraints (e.g., "only free Tuesday mornings").
                        - If constraints are conflicting or too restrictive, note this and suggest alternatives.
                        - If patient_name is missing, flag it as a missing requirement.

                        Determine:
                        1. scheduled_time: suggested appointment time (respecting constraints)
                        2. provider_name: assigned provider
                        3. department: confirmed department
                        4. scheduling_notes: any special instructions or constraint notes
                        5. constraints_applied: list of patient constraints that were respected
                        6. missing_requirements: list of any missing info needed for confirmation
                        7. reasoning: step by step scheduling reasoning

                        Respond in JSON format.
                        """)
                    .user("Urgency: " + urgency + ", Department: " + department +
                          (preferredTime != null ? ", Preferred time: " + preferredTime : "") +
                          ", Patient: " + patientName)
                    .jsonMode();

            GlmClient.GlmResponse response = glmClient.chat(request);
            @SuppressWarnings("unchecked")
            Map<String, Object> scheduleData = mapper.readValue(response.getContent(), Map.class);

            currentStep.setOutput(scheduleData);
            currentStep.setStatus(ReasoningStep.StepStatus.COMPLETED);

            return AgentResult.ok(scheduleData);

        } catch (Exception e) {
            currentStep.setStatus(ReasoningStep.StepStatus.FAILED);
            currentStep.setFallbackPlan("Use default scheduling rules - next available slot in department");
            return fallbackSchedule(input, currentStep);
        }
    }

    private AgentResult fallbackSchedule(Map<String, Object> input, ReasoningStep currentStep) {
        String urgency = (String) input.getOrDefault("urgency_level", "medium");
        String department = (String) input.getOrDefault("department",
                input.getOrDefault("recommended_department", "General Practice"));

        Map<String, Object> data = new HashMap<>();
        if ("critical".equals(urgency)) {
            data.put("scheduled_time", "IMMEDIATE");
            data.put("scheduling_notes", "Critical urgency - walk-in emergency slot assigned");
        } else if ("high".equals(urgency)) {
            data.put("scheduled_time", "Today - Next available");
            data.put("scheduling_notes", "High urgency - same-day slot");
        } else {
            data.put("scheduled_time", "Next business day - 9:00 AM");
            data.put("scheduling_notes", "Standard scheduling");
        }
        data.put("department", department);
        data.put("provider_name", "Next available provider");
        data.put("reasoning", "Rule-based fallback scheduling (GLM unavailable)");
        data.put("fallback_used", true);

        return AgentResult.ok(data);
    }
}

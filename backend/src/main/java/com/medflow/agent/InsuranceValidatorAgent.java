package com.medflow.agent;

import com.medflow.client.GlmClient;
import com.medflow.model.ReasoningStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class InsuranceValidatorAgent implements WorkerAgent {

    private final GlmClient glmClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public InsuranceValidatorAgent(GlmClient glmClient) {
        this.glmClient = glmClient;
    }

    @Override
    public String getName() { return "InsuranceValidatorAgent"; }

    @Override
    public String getDescription() { return "Validates patient insurance coverage and determines eligibility"; }

    @Override
    public boolean canHandle(String intent) {
        return intent != null && (intent.contains("insurance") || intent.contains("coverage")
                || intent.contains("eligibility") || intent.contains("verify"));
    }

    @Override
    public AgentResult execute(Map<String, Object> input, ReasoningStep currentStep) {
        currentStep.setStatus(ReasoningStep.StepStatus.RUNNING);

        try {
            String provider = (String) input.get("insurance_provider");
            String policyNumber = (String) input.get("policy_number");
            String requiredService = (String) input.getOrDefault("required_service", "general_consultation");

            if (provider == null || provider.isEmpty()) {
                currentStep.setStatus(ReasoningStep.StepStatus.FAILED);
                return AgentResult.fail(
                    "Insurance provider not specified",
                    "Ask patient for insurance provider name, or proceed as self-pay"
                );
            }

            if (policyNumber == null || policyNumber.isEmpty()) {
                currentStep.setStatus(ReasoningStep.StepStatus.FAILED);
                return AgentResult.fail(
                    "Policy number missing - cannot verify coverage",
                    "Ask patient for policy number, or attempt verification with provider name only"
                );
            }

            GlmClient.GlmRequest request = new GlmClient.GlmRequest()
                    .system("""
                        You are an insurance verification AI. Given the insurance details and required service,
                        determine:
                        1. coverage_status: "full", "partial", or "denied"
                        2. copay_amount: estimated copay
                        3. pre_authorization_required: boolean
                        4. covered_services: list of covered items
                        5. reasoning: step by step verification reasoning

                        Respond in JSON format.
                        """)
                    .user("Provider: " + provider + ", Policy: " + policyNumber +
                          ", Required service: " + requiredService)
                    .jsonMode();

            GlmClient.GlmResponse response = glmClient.chat(request);
            Map<String, Object> verificationData = mapper.readValue(response.getContent(), Map.class);

            currentStep.setOutput(verificationData);
            currentStep.setStatus(ReasoningStep.StepStatus.COMPLETED);

            return AgentResult.ok(verificationData);

        } catch (Exception e) {
            currentStep.setStatus(ReasoningStep.StepStatus.FAILED);
            currentStep.setFallbackPlan("Manual insurance verification required - flag for staff review");
            return fallbackVerification(input, currentStep);
        }
    }

    private AgentResult fallbackVerification(Map<String, Object> input, ReasoningStep currentStep) {
        Map<String, Object> data = new HashMap<>();
        data.put("coverage_status", "pending_verification");
        data.put("reasoning", "Could not verify insurance automatically - manual review needed");
        data.put("fallback_used", true);
        data.put("requires_manual_review", true);
        data.put("estimated_copay", "TBD - pending verification");

        return AgentResult.ok(data);
    }
}

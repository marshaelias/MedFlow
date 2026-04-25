package com.medflow.brain;

import com.medflow.agent.*;
import com.medflow.client.GlmClient;
import com.medflow.model.*;
import com.medflow.service.WorkflowEventPublisher;
import com.medflow.state.WorkflowStateStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class MedFlowBrain {

    private final GlmClient glmClient;
    private final List<WorkerAgent> agents;
    private final WorkflowStateStore stateStore;
    private final WorkflowEventPublisher eventPublisher;
    private final ObjectMapper mapper = new ObjectMapper();

    public MedFlowBrain(GlmClient glmClient, List<WorkerAgent> agents,
                        WorkflowStateStore stateStore, WorkflowEventPublisher eventPublisher) {
        this.glmClient = glmClient;
        this.agents = agents;
        this.stateStore = stateStore;
        this.eventPublisher = eventPublisher;
    }

    public BrainResponse process(String sessionId, String patientMessage) {
        // 1. Get or create conversation context
        ConversationContext ctx = stateStore.getOrCreateConversation(sessionId);
        ctx.addUserMessage(patientMessage);

        WorkflowState workflow = stateStore.createWorkflow();
        workflow.setStatus(WorkflowState.WorkflowStatus.ANALYZING);

        eventPublisher.publishStatus(sessionId, workflow.getWorkflowId(), "ANALYZING");
        eventPublisher.publishThinking(sessionId, "Brain", "receive_input",
                "Received patient message: \"" + patientMessage + "\"", "input");

        // 2. GLM analyzes intent and extracts structured data
        ReasoningStep analysisStep = new ReasoningStep("Brain", "analyze_input",
                "Analyzing patient input to determine intent and extract structured data");
        workflow.addReasoningStep(analysisStep);
        analysisStep.setStatus(ReasoningStep.StepStatus.RUNNING);
        eventPublisher.publishStep(sessionId, analysisStep);
        eventPublisher.publishThinking(sessionId, "Brain", "analyze_input",
                "Sending to GLM for intent classification and data extraction...", "reasoning");

        BrainAnalysis analysis = analyzeWithGlm(ctx, patientMessage);

        analysisStep.setOutput(Map.of(
            "intent", analysis.intent,
            "extracted_data", analysis.extractedData,
            "missing_fields", analysis.missingFields,
            "reasoning", analysis.reasoning
        ));
        analysisStep.setStatus(ReasoningStep.StepStatus.COMPLETED);
        eventPublisher.publishStep(sessionId, analysisStep);
        eventPublisher.publishThinking(sessionId, "Brain", "analysis_complete",
                "Intent: " + analysis.intent + " | Extracted " + analysis.extractedData.size()
                        + " data points | Missing: " + (analysis.missingFields.isEmpty() ? "none" : String.join(", ", analysis.missingFields)),
                "info");

        // 3. Update patient profile with extracted data
        if (!analysis.extractedData.isEmpty()) {
            String patientId = (String) analysis.extractedData.getOrDefault("patient_id", "default");
            PatientProfile profile = stateStore.getOrCreatePatient(patientId);
            profile.mergeExtractedData(analysis.extractedData);
            ctx.setPatientId(profile.getPatientId());
            workflow.setPatientId(profile.getPatientId());
            stateStore.updatePatient(profile);
            eventPublisher.publishPatientUpdate(sessionId, profile);
        }

        // 3b. Handle conflicts (e.g., expired insurance)
        if (analysis.conflicts != null && !analysis.conflicts.isEmpty()) {
            ReasoningStep conflictStep = new ReasoningStep("Brain", "detect_conflicts",
                    "Detected " + analysis.conflicts.size() + " conflict(s) in patient input");
            conflictStep.setStatus(ReasoningStep.StepStatus.COMPLETED);
            Map<String, Object> conflictOutput = new HashMap<>();
            conflictOutput.put("conflicts", analysis.conflicts);
            conflictStep.setOutput(conflictOutput);
            workflow.addReasoningStep(conflictStep);
            eventPublisher.publishStep(sessionId, conflictStep);

            for (Map<String, String> conflict : analysis.conflicts) {
                eventPublisher.publishThinking(sessionId, "Brain", "conflict_detected",
                        "Conflict: " + conflict.get("description") + " | Action: " + conflict.get("suggested_action"), "warning");
            }

            // If insurance expired, build a conflict response and return early
            boolean hasInsuranceConflict = analysis.conflicts.stream()
                    .anyMatch(c -> "insurance_expired".equals(c.get("type")));
            if (hasInsuranceConflict) {
                workflow.setStatus(WorkflowState.WorkflowStatus.COMPLETED);
                eventPublisher.publishStatus(sessionId, workflow.getWorkflowId(), "COMPLETED");

                StringBuilder conflictMsg = new StringBuilder();
                conflictMsg.append("Conflict Identified:\n\n");
                for (Map<String, String> conflict : analysis.conflicts) {
                    conflictMsg.append("⚠ ").append(conflict.get("description")).append("\n");
                    conflictMsg.append("Suggested Action: ").append(conflict.get("suggested_action")).append("\n\n");
                }
                if (analysis.extractedData.containsKey("symptoms")) {
                    Object symptoms = analysis.extractedData.get("symptoms");
                    if (symptoms instanceof List) {
                        conflictMsg.append("Symptoms noted: ").append(String.join(", ", (List<String>) symptoms)).append("\n\n");
                    }
                }
                conflictMsg.append("I'll proceed with the Self-Pay workflow for now. Would you like me to estimate the costs, or do you have updated insurance information?");

                ctx.addAssistantMessage(conflictMsg.toString());
                eventPublisher.publishMessage(sessionId, conflictMsg.toString());

                stateStore.updateConversation(ctx);
                stateStore.updateWorkflow(workflow);

                return new BrainResponse(
                    workflow.getWorkflowId(), "completed", conflictMsg.toString(),
                    workflow.getReasoningChain(), ctx.getPatientId() != null ? stateStore.getOrCreatePatient(ctx.getPatientId()) : null,
                    Collections.emptyList()
                );
            }
        }

        // 4. Update context
        ctx.setCurrentIntent(analysis.intent);
        analysis.missingFields.forEach(ctx::addMissingField);

        // 5. If there are missing critical fields, pause and ask the patient
        if (!analysis.missingFields.isEmpty() && shouldAskForMissingData(analysis.missingFields, analysis.intent)) {
            workflow.setStatus(WorkflowState.WorkflowStatus.AWAITING_DATA);
            eventPublisher.publishStatus(sessionId, workflow.getWorkflowId(), "AWAITING_DATA");

            ReasoningStep askStep = new ReasoningStep("Brain", "request_missing_data",
                    "Critical missing fields detected: " + String.join(", ", analysis.missingFields)
                    + ". Pausing workflow to request data from patient.");
            askStep.setStatus(ReasoningStep.StepStatus.COMPLETED);
            askStep.setOutput(Map.of("missing_fields", analysis.missingFields));
            workflow.addReasoningStep(askStep);
            eventPublisher.publishStep(sessionId, askStep);

            String prompt = generateMissingDataPrompt(analysis.missingFields, analysis.intent);
            ctx.addAssistantMessage(prompt);
            eventPublisher.publishMessage(sessionId, prompt);
            eventPublisher.publishThinking(sessionId, "Brain", "awaiting_data",
                    "Cannot proceed without: " + String.join(", ", analysis.missingFields), "warning");

            stateStore.updateConversation(ctx);
            stateStore.updateWorkflow(workflow);

            return new BrainResponse(
                workflow.getWorkflowId(), "awaiting_data", prompt,
                workflow.getReasoningChain(), stateStore.getOrCreatePatient(ctx.getPatientId()),
                analysis.missingFields
            );
        }

        // 6. Resolve previously missing fields if patient provided them
        if (!ctx.getMissingDataFields().isEmpty()) {
            ReasoningStep resolveStep = new ReasoningStep("Brain", "resolve_missing_data",
                    "Checking if patient message resolves previously missing fields: "
                    + String.join(", ", ctx.getMissingDataFields()));
            resolveStep.setStatus(ReasoningStep.StepStatus.COMPLETED);
            workflow.addReasoningStep(resolveStep);
            eventPublisher.publishStep(sessionId, resolveStep);

            for (String missing : new ArrayList<>(ctx.getMissingDataFields())) {
                if (analysis.extractedData.containsKey(missing)) {
                    ctx.resolveMissingField(missing);
                    eventPublisher.publishThinking(sessionId, "Brain", "field_resolved",
                            "Resolved missing field: " + missing, "info");
                }
            }
        }

        // 7. Select and execute agents
        workflow.setStatus(WorkflowState.WorkflowStatus.EXECUTING);
        eventPublisher.publishStatus(sessionId, workflow.getWorkflowId(), "EXECUTING");

        List<WorkerAgent> selectedAgents = selectAgents(analysis.intent);

        if (selectedAgents.isEmpty()) {
            workflow.setStatus(WorkflowState.WorkflowStatus.FAILED);
            eventPublisher.publishStatus(sessionId, workflow.getWorkflowId(), "FAILED");

            ReasoningStep failStep = new ReasoningStep("Brain", "no_agent_found",
                    "No suitable agent found for intent: " + analysis.intent
                    + ". Available agents: " + agents.stream().map(WorkerAgent::getName).collect(Collectors.joining(", ")));
            failStep.setStatus(ReasoningStep.StepStatus.FAILED);
            failStep.setFallbackPlan("Ask patient to clarify their request so a suitable agent can be selected");
            workflow.addReasoningStep(failStep);
            eventPublisher.publishStep(sessionId, failStep);

            String msg = "I'm not sure how to handle that request. Could you describe your symptoms or what you need help with?";
            ctx.addAssistantMessage(msg);
            eventPublisher.publishMessage(sessionId, msg);
            eventPublisher.publishThinking(sessionId, "Brain", "no_agent",
                    "Could not route to any agent - asking for clarification", "warning");

            stateStore.updateConversation(ctx);
            stateStore.updateWorkflow(workflow);

            return new BrainResponse(
                workflow.getWorkflowId(), "no_agent", msg,
                workflow.getReasoningChain(), stateStore.getOrCreatePatient(ctx.getPatientId()),
                Collections.emptyList()
            );
        }

        // 8. Agent selection reasoning step
        ReasoningStep selectStep = new ReasoningStep("Brain", "select_agents",
                "Selected agents for intent '" + analysis.intent + "': "
                + selectedAgents.stream().map(WorkerAgent::getName).collect(Collectors.joining(" -> ")));
        selectStep.setStatus(ReasoningStep.StepStatus.COMPLETED);
        selectStep.setOutput(Map.of("selected_agents",
                selectedAgents.stream().map(WorkerAgent::getName).collect(Collectors.toList())));
        workflow.addReasoningStep(selectStep);
        eventPublisher.publishStep(sessionId, selectStep);
        eventPublisher.publishThinking(sessionId, "Brain", "agent_selection",
                "Pipeline: " + selectedAgents.stream().map(WorkerAgent::getName).collect(Collectors.joining(" -> ")),
                "info");

        // 9. Execute the agent pipeline
        Map<String, Object> pipelineInput = buildPipelineInput(analysis, ctx);
        Map<String, Object> accumulatedResults = new HashMap<>(analysis.extractedData);
        boolean hadFailure = false;

        for (int i = 0; i < selectedAgents.size(); i++) {
            WorkerAgent agent = selectedAgents.get(i);
            ReasoningStep agentStep = new ReasoningStep(agent.getName(), "execute",
                    "Executing " + agent.getName() + " for intent: " + analysis.intent);
            workflow.addReasoningStep(agentStep);
            eventPublisher.publishStep(sessionId, agentStep);
            eventPublisher.publishThinking(sessionId, agent.getName(), "executing",
                    agent.getDescription() + "...", "active");

            try {
                pipelineInput.putAll(accumulatedResults);
                AgentResult result = agent.execute(pipelineInput, agentStep);

                if (result.isSuccess()) {
                    accumulatedResults.putAll(result.getData());
                    agentStep.setStatus(ReasoningStep.StepStatus.COMPLETED);
                    agentStep.setOutput(result.getData());
                    eventPublisher.publishStep(sessionId, agentStep);
                    eventPublisher.publishThinking(sessionId, agent.getName(), "completed",
                            agent.getName() + " completed successfully", "info");

                    if (ctx.getPatientId() != null) {
                        PatientProfile profile = stateStore.getOrCreatePatient(ctx.getPatientId());
                        updateProfileFromAgentResult(profile, agent, result);
                        stateStore.updatePatient(profile);
                        eventPublisher.publishPatientUpdate(sessionId, profile);
                    }
                } else {
                    hadFailure = true;
                    agentStep.setStatus(ReasoningStep.StepStatus.FAILED);
                    eventPublisher.publishStep(sessionId, agentStep);

                    // Brain reasons about the failure and decides a fallback
                    ReasoningStep fallbackReasonStep = new ReasoningStep("Brain", "reason_fallback",
                            "Agent " + agent.getName() + " failed: \"" + result.getError()
                            + "\". Reasoning about fallback strategy...");
                    fallbackReasonStep.setStatus(ReasoningStep.StepStatus.RUNNING);
                    workflow.addReasoningStep(fallbackReasonStep);
                    eventPublisher.publishStep(sessionId, fallbackReasonStep);
                    eventPublisher.publishThinking(sessionId, "Brain", "reasoning_fallback",
                            "Agent failed - deciding whether to retry, skip, or use fallback for " + agent.getName(), "warning");

                    FallbackDecision decision = reasonAboutFallback(agent.getName(), result.getError(),
                            result.getFallbackSuggestion(), i < selectedAgents.size() - 1, accumulatedResults);

                    fallbackReasonStep.setStatus(ReasoningStep.StepStatus.COMPLETED);
                    fallbackReasonStep.setOutput(Map.of(
                        "failed_agent", agent.getName(),
                        "error", result.getError(),
                        "decision", decision.action,
                        "reasoning", decision.reasoning
                    ));
                    fallbackReasonStep.setFallbackPlan(decision.action + ": " + decision.reasoning);
                    eventPublisher.publishStep(sessionId, fallbackReasonStep);
                    eventPublisher.publishThinking(sessionId, "Brain", "fallback_decision",
                            "Decision: " + decision.action + " - " + decision.reasoning, "warning");

                    workflow.setStatus(WorkflowState.WorkflowStatus.RECOVERING);
                    eventPublisher.publishStatus(sessionId, workflow.getWorkflowId(), "RECOVERING");

                    switch (decision.action) {
                        case "use_fallback_data":
                            if (decision.fallbackData != null) {
                                accumulatedResults.putAll(decision.fallbackData);
                            }
                            break;
                        case "skip_and_continue":
                            break;
                        case "ask_patient":
                            break;
                        default:
                            break;
                    }
                }
            } catch (Exception e) {
                hadFailure = true;
                agentStep.setStatus(ReasoningStep.StepStatus.FAILED);
                agentStep.setFallbackPlan("Unexpected error: " + e.getMessage());
                eventPublisher.publishStep(sessionId, agentStep);

                ReasoningStep recoveryStep = new ReasoningStep("Brain", "recover_from_error",
                        "Unexpected error in " + agent.getName() + ": " + e.getMessage()
                        + ". Attempting to continue pipeline with available data.");
                recoveryStep.setStatus(ReasoningStep.StepStatus.COMPLETED);
                workflow.addReasoningStep(recoveryStep);
                eventPublisher.publishStep(sessionId, recoveryStep);
                eventPublisher.publishThinking(sessionId, "Brain", "error_recovery",
                        "Recovered from error in " + agent.getName() + " - continuing pipeline", "error");

                workflow.setStatus(WorkflowState.WorkflowStatus.RECOVERING);
                eventPublisher.publishStatus(sessionId, workflow.getWorkflowId(), "RECOVERING");
            }
        }

        // 10. Generate response via GLM
        workflow.setStatus(hadFailure ? WorkflowState.WorkflowStatus.COMPLETED_WITH_RECOVERY : WorkflowState.WorkflowStatus.COMPLETED);
        eventPublisher.publishStatus(sessionId, workflow.getWorkflowId(), hadFailure ? "COMPLETED_WITH_RECOVERY" : "COMPLETED");

        ReasoningStep responseStep = new ReasoningStep("Brain", "generate_response",
                "Generating patient-facing response summary from workflow results");
        responseStep.setStatus(ReasoningStep.StepStatus.RUNNING);
        workflow.addReasoningStep(responseStep);
        eventPublisher.publishStep(sessionId, responseStep);
        eventPublisher.publishThinking(sessionId, "Brain", "generating_response",
                "Composing response with " + accumulatedResults.size() + " data points...", "active");

        String responseText = generateResponse(ctx, accumulatedResults, workflow);

        responseStep.setStatus(ReasoningStep.StepStatus.COMPLETED);
        eventPublisher.publishStep(sessionId, responseStep);
        eventPublisher.publishThinking(sessionId, "Brain", "response_complete",
                "Workflow complete" + (hadFailure ? " (with recovery)" : ""), "info");

        ctx.addAssistantMessage(responseText);
        eventPublisher.publishMessage(sessionId, responseText);

        stateStore.updateConversation(ctx);
        stateStore.updateWorkflow(workflow);

        return new BrainResponse(
            workflow.getWorkflowId(),
            hadFailure ? "completed_with_recovery" : "completed",
            responseText,
            workflow.getReasoningChain(),
            stateStore.getOrCreatePatient(ctx.getPatientId()),
            Collections.emptyList()
        );
    }

    private void updateProfileFromAgentResult(PatientProfile profile, WorkerAgent agent, AgentResult result) {
        if (agent instanceof TriageAgent) {
            profile.setUrgencyLevel((String) result.getData().get("urgency_level"));
            profile.setCondition((String) result.getData().get("likely_condition"));
            if (result.getData().get("recommended_department") != null) {
                if (profile.getAppointment() == null) {
                    profile.setAppointment(new PatientProfile.AppointmentInfo());
                }
                profile.getAppointment().setDepartment((String) result.getData().get("recommended_department"));
            }
        } else if (agent instanceof InsuranceValidatorAgent) {
            PatientProfile.InsuranceInfo ins = profile.getInsurance();
            if (ins == null) {
                ins = new PatientProfile.InsuranceInfo();
            }
            ins.setCoverageStatus((String) result.getData().get("coverage_status"));
            ins.setVerified(!Boolean.TRUE.equals(result.getData().get("fallback_used")));
            if (result.getData().get("copay_amount") != null) {
                ins.setCopayAmount(String.valueOf(result.getData().get("copay_amount")));
            }
            profile.setInsurance(ins);
        } else if (agent instanceof SchedulerAgent) {
            PatientProfile.AppointmentInfo appt = profile.getAppointment();
            if (appt == null) {
                appt = new PatientProfile.AppointmentInfo();
            }
            appt.setScheduledTime((String) result.getData().get("scheduled_time"));
            appt.setDepartment((String) result.getData().get("department"));
            appt.setProvider((String) result.getData().get("provider_name"));
            if (result.getData().get("scheduling_notes") != null) {
                appt.setNotes((String) result.getData().get("scheduling_notes"));
            }
            profile.setAppointment(appt);
        }
    }

    private FallbackDecision reasonAboutFallback(String agentName, String error, String suggestion,
                                                  boolean hasNextAgent, Map<String, Object> currentResults) {
        try {
            GlmClient.GlmRequest request = new GlmClient.GlmRequest()
                    .system("""
                        You are a workflow recovery AI. An agent in a medical workflow pipeline has failed.
                        Decide the best recovery action:
                        - "use_fallback_data": Provide estimated/default data to keep the pipeline moving
                        - "skip_and_continue": Skip this agent and proceed with remaining agents
                        - "ask_patient": Ask the patient for the missing information

                        Respond in JSON:
                        {
                          "action": "use_fallback_data|skip_and_continue|ask_patient",
                          "reasoning": "why this is the best action",
                          "fallback_data": { ... default/estimated data if action is use_fallback_data ... }
                        }
                        """)
                    .user("Failed agent: " + agentName + "\nError: " + error
                          + "\nAgent suggestion: " + suggestion
                          + "\nHas next agent: " + hasNextAgent
                          + "\nCurrent results: " + mapper.writeValueAsString(currentResults))
                    .jsonMode();

            GlmClient.GlmResponse response = glmClient.chat(request);
            Map<String, Object> parsed = mapper.readValue(response.getContent(), Map.class);

            FallbackDecision decision = new FallbackDecision();
            decision.action = (String) parsed.getOrDefault("action", "skip_and_continue");
            decision.reasoning = (String) parsed.getOrDefault("reasoning", "");
            decision.fallbackData = (Map<String, Object>) parsed.get("fallback_data");
            return decision;
        } catch (Exception e) {
            FallbackDecision decision = new FallbackDecision();
            decision.action = "skip_and_continue";
            decision.reasoning = "GLM unavailable for fallback reasoning. " + suggestion;
            return decision;
        }
    }

    private BrainAnalysis analyzeWithGlm(ConversationContext ctx, String message) {
        try {
            String conversationHistory = ctx.getHistory().stream()
                    .limit(10)
                    .map(m -> m.getRole() + ": " + m.getContent())
                    .collect(Collectors.joining("\n"));

            GlmClient.GlmRequest request = new GlmClient.GlmRequest()
                    .system("""
                        You are the reasoning engine of a medical workflow system called MedFlow. Analyze the patient's message carefully and:

                        1. Determine the primary intent. Possible intents: triage, insurance_verification, scheduling, general
                        2. Extract ONLY data explicitly stated by the patient. NEVER fabricate or infer symptoms, conditions, or data not mentioned.
                        3. Detect conflicts or issues (e.g., expired insurance, conflicting symptoms, constraint mismatches)
                        4. Identify any missing critical information needed for the workflow to proceed
                        5. Provide your step-by-step reasoning

                        CRITICAL RULES:
                        - Only extract symptoms the patient EXPLICITLY mentions. Do NOT add related symptoms.
                        - If the patient mentions insurance is expired/cancelled/lapsed, flag it as a conflict and suggest "self-pay" workflow.
                        - If the patient has scheduling constraints (e.g., "only free Tuesday mornings"), extract the constraint separately.
                        - For scheduling, always check if patient_name is missing before triggering the Scheduler.

                        Respond in JSON format:
                        {
                          "intent": "triage|insurance_verification|scheduling|general",
                          "extracted_data": { ... key-value pairs of ONLY explicitly stated info ... },
                          "conflicts": [ { "type": "insurance_expired|missing_data|...", "description": "...", "suggested_action": "..." } ],
                          "missing_fields": [ ... list of missing field names ... ],
                          "reasoning": "step by step reasoning about what the patient needs"
                        }
                        """)
                    .user("Conversation so far:\n" + conversationHistory + "\n\nLatest message: " + message)
                    .jsonMode();

            GlmClient.GlmResponse response = glmClient.chat(request);
            Map<String, Object> parsed = mapper.readValue(response.getContent(), Map.class);

            BrainAnalysis analysis = new BrainAnalysis(
                (String) parsed.getOrDefault("intent", "general"),
                (Map<String, Object>) parsed.getOrDefault("extracted_data", new HashMap<>()),
                (List<String>) parsed.getOrDefault("missing_fields", new ArrayList<>()),
                (String) parsed.getOrDefault("reasoning", "")
            );
            analysis.conflicts = (List<Map<String, String>>) parsed.getOrDefault("conflicts", new ArrayList<>());
            return analysis;
        } catch (Exception e) {
            return fallbackAnalysis(message);
        }
    }

    private BrainAnalysis fallbackAnalysis(String message) {
        String lower = message.toLowerCase();
        String intent = "general";
        Map<String, Object> data = new HashMap<>();
        List<String> missing = new ArrayList<>();
        List<Map<String, String>> conflicts = new ArrayList<>();
        StringBuilder reasoning = new StringBuilder("Rule-based fallback analysis (GLM unavailable). ");

        // Extract explicitly mentioned symptoms only
        List<String> mentionedSymptoms = new ArrayList<>();
        String[] symptomKeywords = {"cough", "fever", "pain", "headache", "dizziness", "dizzy", "nausea",
                "shortness of breath", "fatigue", "chest pain", "back pain", "sore throat", "rash"};
        for (String kw : symptomKeywords) {
            if (lower.contains(kw)) {
                mentionedSymptoms.add(kw.substring(0, 1).toUpperCase() + kw.substring(1));
            }
        }

        if (!mentionedSymptoms.isEmpty()) {
            intent = "triage";
            data.put("symptoms", mentionedSymptoms);
            reasoning.append("Detected symptoms: ").append(String.join(", ", mentionedSymptoms)).append(" -> triage intent. ");
        }
        if (lower.contains("feel") || lower.contains("sick") || lower.contains("hurt")) {
            intent = "triage";
            reasoning.append("Detected symptom-related keywords -> triage intent. ");
        }

        // Detect insurance conflicts
        if (lower.contains("insurance") || lower.contains("coverage") || lower.contains("policy") || lower.contains("copay")) {
            intent = "insurance_verification";
            missing.add("insurance_provider");
            missing.add("policy_number");
            reasoning.append("Detected insurance-related keywords -> insurance_verification intent. ");

            if (lower.contains("expired") || lower.contains("cancelled") || lower.contains("lapsed") || lower.contains("inactive")) {
                Map<String, String> conflict = new HashMap<>();
                conflict.put("type", "insurance_expired");
                conflict.put("description", "Patient reports insurance is expired/cancelled");
                conflict.put("suggested_action", "Switch to Self-Pay workflow. Inform patient of self-pay options and estimated costs.");
                conflicts.add(conflict);
                data.put("insurance_status", "expired");
                reasoning.append("FLAG: Insurance expired detected -> suggest Self-Pay workflow. ");
            }
        }

        if (lower.contains("appointment") || lower.contains("schedule") || lower.contains("book") || lower.contains("visit")) {
            intent = "scheduling";
            missing.add("patient_name");
            reasoning.append("Detected scheduling-related keywords -> scheduling intent. ");

            // Detect time constraints
            String[] dayKeywords = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"};
            String[] timeKeywords = {"morning", "afternoon", "evening", "am", "pm"};
            for (String day : dayKeywords) {
                if (lower.contains(day)) {
                    data.put("preferred_day", day.substring(0, 1).toUpperCase() + day.substring(1));
                    break;
                }
            }
            for (String time : timeKeywords) {
                if (lower.contains(time)) {
                    data.put("preferred_time_slot", time.toUpperCase());
                    break;
                }
            }
            if (data.containsKey("preferred_day") || data.containsKey("preferred_time_slot")) {
                reasoning.append("Detected scheduling constraint -> will check for missing patient info before scheduling. ");
            }
        }

        data.put("raw_message", message);
        BrainAnalysis analysis = new BrainAnalysis(intent, data, missing, reasoning.toString());
        analysis.conflicts = conflicts;
        return analysis;
    }

    private List<WorkerAgent> selectAgents(String intent) {
        List<WorkerAgent> selected = agents.stream()
                .filter(a -> a.canHandle(intent))
                .collect(Collectors.toList());

        if ("triage".equals(intent)) {
            agents.stream()
                    .filter(a -> a instanceof SchedulerAgent)
                    .findFirst()
                    .ifPresent(a -> { if (!selected.contains(a)) selected.add(a); });
        }

        if ("insurance_verification".equals(intent)) {
            agents.stream()
                    .filter(a -> a instanceof SchedulerAgent)
                    .findFirst()
                    .ifPresent(a -> { if (!selected.contains(a)) selected.add(a); });
        }

        return selected;
    }

    private boolean shouldAskForMissingData(List<String> missing, String intent) {
        Set<String> criticalInsurance = Set.of("insurance_provider", "policy_number");
        if ("insurance_verification".equals(intent)) {
            return missing.stream().anyMatch(criticalInsurance::contains);
        }
        if ("triage".equals(intent)) {
            return false;
        }
        if ("scheduling".equals(intent)) {
            return missing.stream().anyMatch(f -> f.equals("patient_name") || f.equals("contact_info"));
        }
        return missing.stream().anyMatch(f -> f.equals("patient_name"));
    }

    private String generateMissingDataPrompt(List<String> missing, String intent) {
        StringBuilder sb = new StringBuilder();
        if ("scheduling".equals(intent)) {
            sb.append("Reasoning & Clarification:\n\n");
            sb.append("I've identified your intent: Scheduling an appointment.\n");

            boolean hasConstraint = missing.stream().noneMatch(f -> f.equals("preferred_time") || f.equals("preferred_day"));
            if (hasConstraint) {
                sb.append("I've noted your scheduling constraint from your message.\n\n");
            }

            sb.append("Missing Data Detected:\n");
            for (String field : missing) {
                sb.append("- ").append(field.replace("_", " ")).append("\n");
            }
            sb.append("\nAction: I need this information before I can trigger the Scheduler. ");
            if (missing.contains("patient_name")) {
                sb.append("Could you please provide your full name?");
            }
            if (missing.contains("contact_info")) {
                sb.append(" Also, a contact number or email would be helpful.");
            }
        } else {
            sb.append("I need a bit more information to help you. Could you please provide:\n");
            for (String field : missing) {
                sb.append("- ").append(field.replace("_", " ")).append("\n");
            }
            if ("insurance_verification".equals(intent)) {
                sb.append("\nThis will help me verify your insurance coverage quickly.");
            }
        }
        return sb.toString();
    }

    private Map<String, Object> buildPipelineInput(BrainAnalysis analysis, ConversationContext ctx) {
        Map<String, Object> input = new HashMap<>(analysis.extractedData);
        input.put("session_id", ctx.getSessionId());
        if (ctx.getPatientId() != null) {
            PatientProfile profile = stateStore.getOrCreatePatient(ctx.getPatientId());
            if (profile.getName() != null) input.put("patient_name", profile.getName());
            if (profile.getUrgencyLevel() != null) input.put("urgency_level", profile.getUrgencyLevel());
            if (profile.getInsurance() != null) {
                if (profile.getInsurance().getProvider() != null) {
                    input.put("insurance_provider", profile.getInsurance().getProvider());
                }
                if (profile.getInsurance().getPolicyNumber() != null) {
                    input.put("policy_number", profile.getInsurance().getPolicyNumber());
                }
            }
            if (profile.getAppointment() != null && profile.getAppointment().getDepartment() != null) {
                input.put("department", profile.getAppointment().getDepartment());
            }
        }
        input.put("message", ctx.getLastUserMessage());
        return input;
    }

    private String generateResponse(ConversationContext ctx, Map<String, Object> results, WorkflowState workflow) {
        try {
            GlmClient.GlmRequest request = new GlmClient.GlmRequest()
                    .system("""
                        You are MedFlow, a medical workflow assistant. Generate a structured response based on the workflow results.

                        FORMAT RULES:
                        - For TRIAGE results: List each extracted symptom with severity. Do NOT add symptoms not mentioned by the patient.
                          Format: "Symptom: [name], Severity: [level]" per line.
                        - For INSURANCE conflicts: Clearly flag the issue and suggest the alternative workflow.
                          Format: "Conflict: [issue]. Suggested Action: [action]"
                        - For SCHEDULING: Identify the intent and any constraints, then flag missing data needed before proceeding.
                          Format: "Intent: [scheduling]. Constraint: [details]. Missing: [fields]. Action: [what to ask]"
                        - Always be precise and never fabricate data.

                        Respond in JSON:
                        {
                          "response": "your structured response text here"
                        }
                        """)
                    .user("Workflow results: " + mapper.writeValueAsString(results)
                          + "\nWorkflow status: " + workflow.getStatus())
                    .jsonMode();

            GlmClient.GlmResponse response = glmClient.chat(request);
            Map<String, Object> parsed = mapper.readValue(response.getContent(), Map.class);
            return (String) parsed.getOrDefault("response", "Your request has been processed. Please check your dashboard for details.");
        } catch (Exception e) {
            StringBuilder sb = new StringBuilder();
            if (results.containsKey("urgency_level")) {
                sb.append("Urgency: ").append(results.get("urgency_level")).append("\n");
            }
            if (results.containsKey("likely_condition")) {
                sb.append("Assessment: ").append(results.get("likely_condition")).append("\n");
            }
            if (results.containsKey("symptoms")) {
                sb.append("Symptoms identified from your message.\n");
            }
            if (results.containsKey("coverage_status")) {
                sb.append("Insurance Status: ").append(results.get("coverage_status")).append("\n");
            }
            if (results.containsKey("insurance_status") && "expired".equals(results.get("insurance_status"))) {
                sb.append("Conflict: Insurance Expired. Suggested Action: Switch to Self-Pay workflow.\n");
            }
            if (results.containsKey("scheduled_time")) {
                sb.append("Appointment: ").append(results.get("scheduled_time")).append("\n");
            }
            if (results.containsKey("recommended_department")) {
                sb.append("Department: ").append(results.get("recommended_department")).append("\n");
            }
            return sb.length() > 0 ? sb.toString() : "Your request has been processed.";
        }
    }

    private static class BrainAnalysis {
        String intent;
        Map<String, Object> extractedData;
        List<String> missingFields;
        String reasoning;
        List<Map<String, String>> conflicts = new ArrayList<>();

        BrainAnalysis(String intent, Map<String, Object> extractedData, List<String> missingFields, String reasoning) {
            this.intent = intent;
            this.extractedData = extractedData;
            this.missingFields = missingFields;
            this.reasoning = reasoning;
        }
    }

    private static class FallbackDecision {
        String action;
        String reasoning;
        Map<String, Object> fallbackData;
    }

    public static class BrainResponse {
        private String workflowId;
        private String status;
        private String message;
        private List<ReasoningStep> reasoningChain;
        private PatientProfile patientProfile;
        private List<String> missingFields;

        public BrainResponse(String workflowId, String status, String message,
                           List<ReasoningStep> reasoningChain, PatientProfile patientProfile,
                           List<String> missingFields) {
            this.workflowId = workflowId;
            this.status = status;
            this.message = message;
            this.reasoningChain = reasoningChain;
            this.patientProfile = patientProfile;
            this.missingFields = missingFields;
        }

        public String getWorkflowId() { return workflowId; }
        public String getStatus() { return status; }
        public String getMessage() { return message; }
        public List<ReasoningStep> getReasoningChain() { return reasoningChain; }
        public PatientProfile getPatientProfile() { return patientProfile; }
        public List<String> getMissingFields() { return missingFields; }
    }
}

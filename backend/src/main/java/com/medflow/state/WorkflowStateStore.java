package com.medflow.state;

import com.medflow.model.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WorkflowStateStore {
    private final Map<String, WorkflowState> workflows = new ConcurrentHashMap<>();
    private final Map<String, PatientProfile> patients = new ConcurrentHashMap<>();
    private final Map<String, ConversationContext> conversations = new ConcurrentHashMap<>();

    public WorkflowState createWorkflow() {
        WorkflowState ws = new WorkflowState();
        workflows.put(ws.getWorkflowId(), ws);
        return ws;
    }

    public WorkflowState getWorkflow(String id) {
        return workflows.get(id);
    }

    public void updateWorkflow(WorkflowState ws) {
        workflows.put(ws.getWorkflowId(), ws);
    }

    public PatientProfile getOrCreatePatient(String patientId) {
        return patients.computeIfAbsent(patientId, k -> new PatientProfile());
    }

    public void updatePatient(PatientProfile profile) {
        patients.put(profile.getPatientId(), profile);
    }

    public Collection<PatientProfile> getAllPatients() {
        return patients.values();
    }

    public ConversationContext getOrCreateConversation(String sessionId) {
        return conversations.computeIfAbsent(sessionId, k -> new ConversationContext(k));
    }

    public void updateConversation(ConversationContext ctx) {
        conversations.put(ctx.getSessionId(), ctx);
    }
}

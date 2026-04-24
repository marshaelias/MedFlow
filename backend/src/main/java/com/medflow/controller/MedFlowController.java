package com.medflow.controller;

import com.medflow.brain.MedFlowBrain;
import com.medflow.model.*;
import com.medflow.service.WorkflowEventPublisher;
import com.medflow.state.WorkflowStateStore;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class MedFlowController {

    private final MedFlowBrain brain;
    private final WorkflowStateStore stateStore;
    private final WorkflowEventPublisher eventPublisher;

    public MedFlowController(MedFlowBrain brain, WorkflowStateStore stateStore,
                             WorkflowEventPublisher eventPublisher) {
        this.brain = brain;
        this.stateStore = stateStore;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> request) {
        String sessionId = request.getOrDefault("session_id", UUID.randomUUID().toString());
        String message = request.getOrDefault("message", "");

        if (message.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message cannot be empty"));
        }

        MedFlowBrain.BrainResponse response = brain.process(sessionId, message);

        Map<String, Object> result = new HashMap<>();
        result.put("workflow_id", response.getWorkflowId());
        result.put("status", response.getStatus());
        result.put("message", response.getMessage());
        result.put("reasoning_chain", response.getReasoningChain());
        result.put("patient_profile", response.getPatientProfile());
        result.put("missing_fields", response.getMissingFields());
        result.put("session_id", sessionId);

        return ResponseEntity.ok(result);
    }

    @GetMapping(value = "/events/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(@PathVariable String sessionId) {
        SseEmitter emitter = eventPublisher.createEmitter(sessionId);

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"session\":\"" + sessionId + "\"}"));
        } catch (Exception e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }

    @GetMapping("/workflow/{id}")
    public ResponseEntity<WorkflowState> getWorkflow(@PathVariable String id) {
        WorkflowState ws = stateStore.getWorkflow(id);
        if (ws == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ws);
    }

    @GetMapping("/patient/{id}")
    public ResponseEntity<PatientProfile> getPatient(@PathVariable String id) {
        PatientProfile profile = stateStore.getOrCreatePatient(id);
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/patients")
    public ResponseEntity<Collection<PatientProfile>> getAllPatients() {
        return ResponseEntity.ok(stateStore.getAllPatients());
    }

    @GetMapping("/conversation/{sessionId}")
    public ResponseEntity<ConversationContext> getConversation(@PathVariable String sessionId) {
        ConversationContext ctx = stateStore.getOrCreateConversation(sessionId);
        return ResponseEntity.ok(ctx);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "healthy",
            "service", "MedFlow Brain",
            "agents", "TriageAgent, InsuranceValidatorAgent, SchedulerAgent"
        ));
    }
}

package com.medflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medflow.model.ReasoningStep;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class WorkflowEventPublisher {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, List<SseEmitter>> sessionEmitters = new ConcurrentHashMap<>();

    public SseEmitter createEmitter(String sessionId) {
        SseEmitter emitter = new SseEmitter(300_000L);
        sessionEmitters.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(sessionId, emitter));
        emitter.onTimeout(() -> removeEmitter(sessionId, emitter));
        emitter.onError(e -> removeEmitter(sessionId, emitter));

        return emitter;
    }

    public void publishStep(String sessionId, ReasoningStep step) {
        publish(sessionId, "reasoning_step", step);
    }

    public void publishStatus(String sessionId, String workflowId, String status) {
        Map<String, String> data = Map.of("workflow_id", workflowId, "status", status);
        publish(sessionId, "workflow_status", data);
    }

    public void publishPatientUpdate(String sessionId, Object patientProfile) {
        publish(sessionId, "patient_update", patientProfile);
    }

    public void publishMessage(String sessionId, String message) {
        publish(sessionId, "assistant_message", Map.of("message", message));
    }

    public void publishThinking(String sessionId, String agent, String action, String content, String type) {
        Map<String, String> data = new HashMap<>();
        data.put("agent", agent);
        data.put("action", action);
        data.put("content", content);
        data.put("type", type);
        publish(sessionId, "thinking", data);
    }

    private void publish(String sessionId, String eventType, Object data) {
        List<SseEmitter> emitters = sessionEmitters.get(sessionId);
        if (emitters == null) return;

        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                String json = mapper.writeValueAsString(data);
                emitter.send(SseEmitter.event().name(eventType).data(json));
            } catch (IOException e) {
                dead.add(emitter);
            }
        }
        dead.forEach(e -> removeEmitter(sessionId, e));
    }

    private void removeEmitter(String sessionId, SseEmitter emitter) {
        List<SseEmitter> emitters = sessionEmitters.get(sessionId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                sessionEmitters.remove(sessionId);
            }
        }
    }
}

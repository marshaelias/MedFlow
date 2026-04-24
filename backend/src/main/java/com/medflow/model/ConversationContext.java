package com.medflow.model;

import java.time.Instant;
import java.util.*;

public class ConversationContext {
    private String sessionId;
    private String patientId;
    private List<ConversationMessage> history;
    private String currentIntent;
    private Set<String> missingDataFields;
    private Map<String, Object> contextData;
    private Instant createdAt;
    private Instant lastActivity;

    public static class ConversationMessage {
        private String role;
        private String content;
        private Instant timestamp;
        private Map<String, Object> metadata;

        public ConversationMessage() {}

        public ConversationMessage(String role, String content) {
            this.role = role;
            this.content = content;
            this.timestamp = Instant.now();
            this.metadata = new HashMap<>();
        }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }

    public ConversationContext() {
        this.sessionId = UUID.randomUUID().toString();
        this.history = new ArrayList<>();
        this.missingDataFields = new HashSet<>();
        this.contextData = new HashMap<>();
        this.createdAt = Instant.now();
        this.lastActivity = Instant.now();
    }

    public ConversationContext(String sessionId) {
        this();
        this.sessionId = sessionId;
    }

    public void addUserMessage(String content) {
        history.add(new ConversationMessage("user", content));
        this.lastActivity = Instant.now();
    }

    public void addAssistantMessage(String content) {
        history.add(new ConversationMessage("assistant", content));
        this.lastActivity = Instant.now();
    }

    public String getLastUserMessage() {
        return history.stream()
                .filter(m -> "user".equals(m.getRole()))
                .reduce((first, second) -> second)
                .map(ConversationMessage::getContent)
                .orElse(null);
    }

    public void resolveMissingField(String field) {
        missingDataFields.remove(field);
        this.lastActivity = Instant.now();
    }

    public void addMissingField(String field) {
        this.missingDataFields.add(field);
        this.lastActivity = Instant.now();
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; this.lastActivity = Instant.now(); }
    public List<ConversationMessage> getHistory() { return history; }
    public void setHistory(List<ConversationMessage> history) { this.history = history; }
    public String getCurrentIntent() { return currentIntent; }
    public void setCurrentIntent(String currentIntent) { this.currentIntent = currentIntent; this.lastActivity = Instant.now(); }
    public Set<String> getMissingDataFields() { return missingDataFields; }
    public void setMissingDataFields(Set<String> missingDataFields) { this.missingDataFields = missingDataFields; }
    public Map<String, Object> getContextData() { return contextData; }
    public void setContextData(Map<String, Object> contextData) { this.contextData = contextData; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastActivity() { return lastActivity; }
    public void setLastActivity(Instant lastActivity) { this.lastActivity = lastActivity; }
}

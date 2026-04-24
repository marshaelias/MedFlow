package com.medflow.model;

import java.time.Instant;
import java.util.*;

public class PatientProfile {
    private String patientId;
    private String name;
    private Integer age;
    private String gender;
    private List<String> symptoms;
    private String condition;
    private String urgencyLevel;
    private InsuranceInfo insurance;
    private AppointmentInfo appointment;
    private Map<String, Object> extractedData;
    private Instant lastUpdated;

    public static class InsuranceInfo {
        private String provider;
        private String policyNumber;
        private Boolean verified;
        private String coverageStatus;
        private String copayAmount;
        private Boolean preAuthRequired;

        public InsuranceInfo() {}

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getPolicyNumber() { return policyNumber; }
        public void setPolicyNumber(String policyNumber) { this.policyNumber = policyNumber; }
        public Boolean getVerified() { return verified; }
        public void setVerified(Boolean verified) { this.verified = verified; }
        public String getCoverageStatus() { return coverageStatus; }
        public void setCoverageStatus(String coverageStatus) { this.coverageStatus = coverageStatus; }
        public String getCopayAmount() { return copayAmount; }
        public void setCopayAmount(String copayAmount) { this.copayAmount = copayAmount; }
        public Boolean getPreAuthRequired() { return preAuthRequired; }
        public void setPreAuthRequired(Boolean preAuthRequired) { this.preAuthRequired = preAuthRequired; }
    }

    public static class AppointmentInfo {
        private String scheduledTime;
        private String department;
        private String provider;
        private String notes;

        public AppointmentInfo() {}

        public String getScheduledTime() { return scheduledTime; }
        public void setScheduledTime(String scheduledTime) { this.scheduledTime = scheduledTime; }
        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    public PatientProfile() {
        this.patientId = UUID.randomUUID().toString();
        this.symptoms = new ArrayList<>();
        this.extractedData = new HashMap<>();
        this.lastUpdated = Instant.now();
    }

    public void mergeExtractedData(Map<String, Object> newData) {
        extractedData.putAll(newData);
        if (newData.containsKey("name")) this.name = (String) newData.get("name");
        if (newData.containsKey("patient_name") && this.name == null) this.name = (String) newData.get("patient_name");
        if (newData.containsKey("age") && newData.get("age") != null) {
            this.age = ((Number) newData.get("age")).intValue();
        }
        if (newData.containsKey("gender")) this.gender = (String) newData.get("gender");
        if (newData.containsKey("condition")) this.condition = (String) newData.get("condition");
        if (newData.containsKey("likely_condition") && this.condition == null) this.condition = (String) newData.get("likely_condition");
        if (newData.containsKey("urgency_level")) this.urgencyLevel = (String) newData.get("urgency_level");
        if (newData.containsKey("urgency") && this.urgencyLevel == null) this.urgencyLevel = (String) newData.get("urgency");
        if (newData.containsKey("symptoms") && newData.get("symptoms") instanceof List) {
            this.symptoms = (List<String>) newData.get("symptoms");
        }
        if (newData.containsKey("insurance_provider")) {
            if (this.insurance == null) this.insurance = new InsuranceInfo();
            this.insurance.setProvider((String) newData.get("insurance_provider"));
        }
        if (newData.containsKey("policy_number") && this.insurance != null) {
            this.insurance.setPolicyNumber((String) newData.get("policy_number"));
        }
        this.lastUpdated = Instant.now();
    }

    public String getPatientId() { return patientId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public List<String> getSymptoms() { return symptoms; }
    public void setSymptoms(List<String> symptoms) { this.symptoms = symptoms; }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
    public String getUrgencyLevel() { return urgencyLevel; }
    public void setUrgencyLevel(String urgencyLevel) { this.urgencyLevel = urgencyLevel; }
    public InsuranceInfo getInsurance() { return insurance; }
    public void setInsurance(InsuranceInfo insurance) { this.insurance = insurance; }
    public AppointmentInfo getAppointment() { return appointment; }
    public void setAppointment(AppointmentInfo appointment) { this.appointment = appointment; }
    public Map<String, Object> getExtractedData() { return extractedData; }
    public void setExtractedData(Map<String, Object> extractedData) { this.extractedData = extractedData; }
    public Instant getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }
}

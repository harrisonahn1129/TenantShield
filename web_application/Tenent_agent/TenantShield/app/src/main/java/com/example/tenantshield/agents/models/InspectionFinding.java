package com.example.tenantshield.agents.models;

public class InspectionFinding {

    public enum Severity {
        LOW, MODERATE, HIGH, CRITICAL
    }

    private String category;
    private String description;
    private Severity severity;
    private String location;
    private String evidence;

    public InspectionFinding() {
    }

    public InspectionFinding(String category, String description, Severity severity,
                             String location, String evidence) {
        this.category = category;
        this.description = description;
        this.severity = severity;
        this.location = location;
        this.evidence = evidence;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }
}

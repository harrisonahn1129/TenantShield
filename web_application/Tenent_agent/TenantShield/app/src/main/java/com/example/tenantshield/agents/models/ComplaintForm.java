package com.example.tenantshield.agents.models;

import java.util.List;

public class ComplaintForm {

    public static class ViolationEntry {

        private String classification;
        private String date;
        private String description;

        public ViolationEntry() {
        }

        public ViolationEntry(String classification, String date, String description) {
            this.classification = classification;
            this.date = date;
            this.description = description;
        }

        public String getClassification() {
            return classification;
        }

        public void setClassification(String classification) {
            this.classification = classification;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    private String documentId;
    private String filingDate;
    private String address;
    private String tenantName;
    private String natureOfComplaint;
    private String hazardClass;
    private String hazardDescription;
    private List<ViolationEntry> violationHistory;
    private List<String> evidenceImagePaths;
    private String inspectorSignature;
    private String verificationToken;
    private long generatedAt;

    public ComplaintForm() {
    }

    public ComplaintForm(String documentId, String filingDate, String address, String tenantName,
                         String natureOfComplaint, String hazardClass, String hazardDescription,
                         List<ViolationEntry> violationHistory, List<String> evidenceImagePaths,
                         String inspectorSignature, String verificationToken, long generatedAt) {
        this.documentId = documentId;
        this.filingDate = filingDate;
        this.address = address;
        this.tenantName = tenantName;
        this.natureOfComplaint = natureOfComplaint;
        this.hazardClass = hazardClass;
        this.hazardDescription = hazardDescription;
        this.violationHistory = violationHistory;
        this.evidenceImagePaths = evidenceImagePaths;
        this.inspectorSignature = inspectorSignature;
        this.verificationToken = verificationToken;
        this.generatedAt = generatedAt;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getFilingDate() {
        return filingDate;
    }

    public void setFilingDate(String filingDate) {
        this.filingDate = filingDate;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public String getNatureOfComplaint() {
        return natureOfComplaint;
    }

    public void setNatureOfComplaint(String natureOfComplaint) {
        this.natureOfComplaint = natureOfComplaint;
    }

    public String getHazardClass() {
        return hazardClass;
    }

    public void setHazardClass(String hazardClass) {
        this.hazardClass = hazardClass;
    }

    public String getHazardDescription() {
        return hazardDescription;
    }

    public void setHazardDescription(String hazardDescription) {
        this.hazardDescription = hazardDescription;
    }

    public List<ViolationEntry> getViolationHistory() {
        return violationHistory;
    }

    public void setViolationHistory(List<ViolationEntry> violationHistory) {
        this.violationHistory = violationHistory;
    }

    public List<String> getEvidenceImagePaths() {
        return evidenceImagePaths;
    }

    public void setEvidenceImagePaths(List<String> evidenceImagePaths) {
        this.evidenceImagePaths = evidenceImagePaths;
    }

    public String getInspectorSignature() {
        return inspectorSignature;
    }

    public void setInspectorSignature(String inspectorSignature) {
        this.inspectorSignature = inspectorSignature;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    public void setVerificationToken(String verificationToken) {
        this.verificationToken = verificationToken;
    }

    public long getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(long generatedAt) {
        this.generatedAt = generatedAt;
    }
}

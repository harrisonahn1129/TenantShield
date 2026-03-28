package com.example.tenantshield.agents.models;

import java.util.List;

public class InspectionResult {

    public enum HazardLevel {
        NONE, CLASS_A, CLASS_B, CLASS_C
    }

    private HazardLevel hazardLevel;
    private String overallSeverity;
    private List<InspectionFinding> findings;
    private List<String> recommendedActions;
    private List<String> analyzedImagePaths;
    private String rawAnalysis;
    private long inspectedAt;

    public InspectionResult() {
    }

    public InspectionResult(HazardLevel hazardLevel, String overallSeverity,
                            List<InspectionFinding> findings, List<String> recommendedActions,
                            List<String> analyzedImagePaths, String rawAnalysis, long inspectedAt) {
        this.hazardLevel = hazardLevel;
        this.overallSeverity = overallSeverity;
        this.findings = findings;
        this.recommendedActions = recommendedActions;
        this.analyzedImagePaths = analyzedImagePaths;
        this.rawAnalysis = rawAnalysis;
        this.inspectedAt = inspectedAt;
    }

    public HazardLevel getHazardLevel() {
        return hazardLevel;
    }

    public void setHazardLevel(HazardLevel hazardLevel) {
        this.hazardLevel = hazardLevel;
    }

    public String getOverallSeverity() {
        return overallSeverity;
    }

    public void setOverallSeverity(String overallSeverity) {
        this.overallSeverity = overallSeverity;
    }

    public List<InspectionFinding> getFindings() {
        return findings;
    }

    public void setFindings(List<InspectionFinding> findings) {
        this.findings = findings;
    }

    public List<String> getRecommendedActions() {
        return recommendedActions;
    }

    public void setRecommendedActions(List<String> recommendedActions) {
        this.recommendedActions = recommendedActions;
    }

    public List<String> getAnalyzedImagePaths() {
        return analyzedImagePaths;
    }

    public void setAnalyzedImagePaths(List<String> analyzedImagePaths) {
        this.analyzedImagePaths = analyzedImagePaths;
    }

    public String getRawAnalysis() {
        return rawAnalysis;
    }

    public void setRawAnalysis(String rawAnalysis) {
        this.rawAnalysis = rawAnalysis;
    }

    public long getInspectedAt() {
        return inspectedAt;
    }

    public void setInspectedAt(long inspectedAt) {
        this.inspectedAt = inspectedAt;
    }
}

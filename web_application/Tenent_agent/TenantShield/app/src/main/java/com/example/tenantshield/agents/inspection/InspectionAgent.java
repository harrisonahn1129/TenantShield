package com.example.tenantshield.agents.inspection;

import com.example.tenantshield.agents.service.GeminiApiService;
import com.example.tenantshield.agents.models.InspectionResult;
import com.example.tenantshield.agents.models.InspectionFinding;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

import android.util.Log;

public class InspectionAgent {

    private static final String TAG = "InspectionAgent";

    private static final String SYSTEM_PROMPT =
            "You are a certified building inspection AI for TenantShield. Analyze the provided images for: "
            + "mold growth, water damage, structural issues, electrical hazards, pest evidence, fire safety violations, "
            + "lead paint indicators. Classify hazards using NYC Housing Maintenance Code: "
            + "CLASS A (non-hazardous, minor), CLASS B (hazardous, correct within 30 days), "
            + "CLASS C (immediately hazardous, correct within 24 hours). "
            + "Respond with ONLY valid JSON: "
            + "{\"hazard_level\": \"NONE|CLASS_A|CLASS_B|CLASS_C\", "
            + "\"overall_severity\": \"minor|moderate|severe|critical\", "
            + "\"findings\": [{\"category\": \"string\", \"description\": \"string\", "
            + "\"severity\": \"LOW|MODERATE|HIGH|CRITICAL\", \"location\": \"string\", \"evidence\": \"string\"}], "
            + "\"recommended_actions\": [\"string\"], "
            + "\"raw_analysis\": \"2-3 paragraph summary\"}";

    private final GeminiApiService apiService;

    public interface InspectionCallback {
        void onInspectionComplete(InspectionResult result);
        void onError(String message);
    }

    public InspectionAgent(GeminiApiService apiService) {
        this.apiService = apiService;
    }

    public void analyzeImages(List<String> imagePaths, InspectionCallback callback) {
        String prompt = "Analyze these " + imagePaths.size() + " inspection images and provide detailed findings.";

        apiService.generateContentWithImages(SYSTEM_PROMPT, prompt, imagePaths, new GeminiApiService.GeminiCallback() {
            @Override
            public void onSuccess(String responseText) {
                try {
                    JsonObject json = JsonParser.parseString(responseText).getAsJsonObject();

                    InspectionResult result = new InspectionResult();

                    // Parse hazard level
                    String hazardLevelStr = json.get("hazard_level").getAsString();
                    result.setHazardLevel(parseHazardLevel(hazardLevelStr));

                    // Parse overall severity
                    result.setOverallSeverity(json.get("overall_severity").getAsString());

                    // Parse findings
                    List<InspectionFinding> findings = new ArrayList<>();
                    JsonArray findingsArray = json.getAsJsonArray("findings");
                    for (int i = 0; i < findingsArray.size(); i++) {
                        JsonObject findingJson = findingsArray.get(i).getAsJsonObject();
                        InspectionFinding finding = new InspectionFinding();
                        finding.setCategory(findingJson.get("category").getAsString());
                        finding.setDescription(findingJson.get("description").getAsString());
                        finding.setSeverity(parseSeverity(findingJson.get("severity").getAsString()));
                        finding.setLocation(findingJson.get("location").getAsString());
                        finding.setEvidence(findingJson.get("evidence").getAsString());
                        findings.add(finding);
                    }
                    result.setFindings(findings);

                    // Parse recommended actions
                    List<String> actions = new ArrayList<>();
                    JsonArray actionsArray = json.getAsJsonArray("recommended_actions");
                    for (int i = 0; i < actionsArray.size(); i++) {
                        actions.add(actionsArray.get(i).getAsString());
                    }
                    result.setRecommendedActions(actions);

                    // Parse raw analysis
                    result.setRawAnalysis(json.get("raw_analysis").getAsString());

                    // Set metadata
                    result.setAnalyzedImagePaths(imagePaths);
                    result.setInspectedAt(System.currentTimeMillis());

                    callback.onInspectionComplete(result);
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing inspection response", e);
                    callback.onError("Failed to parse inspection results: " + e.getMessage());
                }
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Inspection request error: " + message);
                callback.onError(message);
            }
        });
    }

    private InspectionResult.HazardLevel parseHazardLevel(String level) {
        switch (level.toUpperCase()) {
            case "CLASS_A":
                return InspectionResult.HazardLevel.CLASS_A;
            case "CLASS_B":
                return InspectionResult.HazardLevel.CLASS_B;
            case "CLASS_C":
                return InspectionResult.HazardLevel.CLASS_C;
            case "NONE":
            default:
                return InspectionResult.HazardLevel.NONE;
        }
    }

    private InspectionFinding.Severity parseSeverity(String severity) {
        switch (severity.toUpperCase()) {
            case "MODERATE":
                return InspectionFinding.Severity.MODERATE;
            case "HIGH":
                return InspectionFinding.Severity.HIGH;
            case "CRITICAL":
                return InspectionFinding.Severity.CRITICAL;
            case "LOW":
            default:
                return InspectionFinding.Severity.LOW;
        }
    }
}

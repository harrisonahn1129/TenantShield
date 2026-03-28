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
            "You are a certified NYC building inspection AI for TenantShield. "
            + "Analyze the provided images for housing code violations.\n\n"
            + "INSPECTION PROTOCOL — OBSERVE FIRST, CLASSIFY LAST:\n"
            + "1. FIRST describe exactly what you observe in the image(s). Be specific about location, extent, and condition.\n"
            + "2. Then assess whether it constitutes a code violation or is normal wear and tear.\n"
            + "3. ONLY THEN classify using the rules below.\n\n"
            + "HAZARD CLASSIFICATION RULES (NYC Housing Maintenance Code):\n\n"
            + "CLASS C — Immediately Hazardous (correct within 24 hours):\n"
            + "- HMC-01: Water leak near electrical outlets/fixtures (§27-2031)\n"
            + "- HMC-03: No heat in heating season Oct 1 - May 31 (§27-2029)\n"
            + "- HMC-04: No hot water (§27-2031)\n"
            + "- HMC-05: Ceiling collapse, active sagging, structural failure risk (§27-2005)\n"
            + "- HMC-07: Broken/missing lock on entry door (§27-2043)\n"
            + "- HMC-08: Lead paint peeling WITH child under 6 present (§27-2056.4)\n"
            + "- HMC-09: Gas leak or gas odor (§27-2031)\n"
            + "- HMC-12: Missing/non-functional smoke or CO detector (§27-2046.1)\n"
            + "- HMC-14: Sewage backup or overflow (§27-2031)\n\n"
            + "CLASS B — Hazardous (correct within 30 days):\n"
            + "- HMC-02a: Mold covering area OVER 10 square feet (§27-2017.1)\n"
            + "- HMC-06: Pest infestation — roaches, rats, mice, bedbugs (§27-2018)\n"
            + "- HMC-10: Broken elevator in building 6+ stories (§27-989)\n"
            + "- HMC-11: Broken or cracked window (§27-2005)\n"
            + "- HMC-13: Illegal room partition or subdivision (§27-2004)\n"
            + "- HMC-16: Water leak/damage WITHOUT electrical proximity (§27-2005)\n\n"
            + "CLASS A — Non-Hazardous (correct within 90 days):\n"
            + "- HMC-02b: Small mold UNDER 10 square feet (§27-2013)\n"
            + "- HMC-15: Paint peeling/chipping without lead risk (§27-2013)\n"
            + "- Minor plaster cracks, cosmetic damage\n\n"
            + "DO NOT CLASSIFY AS HAZARDS:\n"
            + "- Normal wear and tear (scuff marks, faded paint)\n"
            + "- Old dried stains with no active leak\n"
            + "- Cosmetic discoloration that is not peeling/flaking\n"
            + "- Mold that appears small unless clearly extensive in the image\n"
            + "- Water damage WITHOUT confirmed electrical proximity (classify as A or B, not C)\n\n"
            + "WHEN UNSURE: Classify LOWER (A or B), not higher. Never over-classify.\n"
            + "Never invent legal statutes — use ONLY the HMC codes listed above.\n\n"
            + "Respond with ONLY valid JSON:\n"
            + "{\"hazard_level\": \"NONE|CLASS_A|CLASS_B|CLASS_C\", "
            + "\"overall_severity\": \"minor|moderate|severe|critical\", "
            + "\"findings\": [{\"category\": \"string (use HMC code e.g. HMC-02a mold_large)\", "
            + "\"description\": \"string (what you observe)\", "
            + "\"severity\": \"LOW|MODERATE|HIGH|CRITICAL\", "
            + "\"location\": \"string (where in the image)\", "
            + "\"evidence\": \"string (specific visual evidence)\"}], "
            + "\"recommended_actions\": [\"string\"], "
            + "\"raw_analysis\": \"2-3 paragraph summary including what you observed, "
            + "the classification reasoning, and the applicable HMC statute\"}";

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

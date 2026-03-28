package com.example.tenantshield.agents.filing;

import com.example.tenantshield.agents.service.GeminiApiService;
import com.example.tenantshield.agents.models.UserInfo;
import com.example.tenantshield.agents.models.InspectionResult;
import com.example.tenantshield.agents.models.ComplaintForm;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

import android.util.Log;

public class FilingAgent {

    private static final String TAG = "FilingAgent";

    private static final String SYSTEM_PROMPT =
            "You are a legal document generation AI for TenantShield. Generate a formal housing complaint form "
            + "based on the provided tenant information and inspection results. Follow NYC Housing Maintenance Code formatting. "
            + "Respond with ONLY valid JSON: "
            + "{\"document_id\": \"TS-XXXXX-X\", \"filing_date\": \"MONTH DD, YYYY\", \"address\": \"string\", "
            + "\"tenant_name\": \"Last, First\", \"nature_of_complaint\": \"formal legal description\", "
            + "\"hazard_class\": \"CLASS A|CLASS B|CLASS C\", \"hazard_description\": \"string\", "
            + "\"violation_history\": [{\"classification\": \"CLASS X: DESCRIPTION\", \"date\": \"MM/DD/YYYY\", "
            + "\"description\": \"string\"}], \"inspector_signature\": \"AI Inspector\", "
            + "\"verification_token\": \"hash-like token\"}";

    private final GeminiApiService apiService;
    private final Gson gson;

    public interface FilingCallback {
        void onComplaintReady(ComplaintForm form);
        void onError(String message);
    }

    public FilingAgent(GeminiApiService apiService) {
        this.apiService = apiService;
        this.gson = new Gson();
    }

    public void generateComplaint(UserInfo userInfo, InspectionResult result, FilingCallback callback) {
        String prompt = "Generate a formal complaint for:\nTenant: " + gson.toJson(userInfo)
                + "\nInspection Results: " + gson.toJson(result);

        apiService.generateContent(SYSTEM_PROMPT, prompt, new GeminiApiService.GeminiCallback() {
            @Override
            public void onSuccess(String responseText) {
                try {
                    JsonObject json = JsonParser.parseString(responseText).getAsJsonObject();

                    ComplaintForm form = new ComplaintForm();
                    form.setDocumentId(json.get("document_id").getAsString());
                    form.setFilingDate(json.get("filing_date").getAsString());
                    form.setAddress(json.get("address").getAsString());
                    form.setTenantName(json.get("tenant_name").getAsString());
                    form.setNatureOfComplaint(json.get("nature_of_complaint").getAsString());
                    form.setHazardClass(json.get("hazard_class").getAsString());
                    form.setHazardDescription(json.get("hazard_description").getAsString());
                    form.setInspectorSignature(json.get("inspector_signature").getAsString());
                    form.setVerificationToken(json.get("verification_token").getAsString());

                    // Parse violation history
                    List<ComplaintForm.ViolationEntry> violations = new ArrayList<>();
                    JsonArray violationArray = json.getAsJsonArray("violation_history");
                    for (int i = 0; i < violationArray.size(); i++) {
                        JsonObject violationJson = violationArray.get(i).getAsJsonObject();
                        ComplaintForm.ViolationEntry entry = new ComplaintForm.ViolationEntry();
                        entry.setClassification(violationJson.get("classification").getAsString());
                        entry.setDate(violationJson.get("date").getAsString());
                        entry.setDescription(violationJson.get("description").getAsString());
                        violations.add(entry);
                    }
                    form.setViolationHistory(violations);

                    // Set evidence image paths from inspection result
                    form.setEvidenceImagePaths(result.getAnalyzedImagePaths());

                    // Set generation timestamp
                    form.setGeneratedAt(System.currentTimeMillis());

                    callback.onComplaintReady(form);
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing complaint form response", e);
                    callback.onError("Failed to parse complaint form: " + e.getMessage());
                }
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Filing request error: " + message);
                callback.onError(message);
            }
        });
    }
}

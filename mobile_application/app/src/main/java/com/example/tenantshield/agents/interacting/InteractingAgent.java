package com.example.tenantshield.agents.interacting;

import com.example.tenantshield.agents.service.GeminiApiService;
import com.example.tenantshield.agents.service.VoiceService;
import com.example.tenantshield.agents.models.UserInfo;
import com.example.tenantshield.agents.models.InspectionResult;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

import android.util.Log;

/**
 * Interacting Agent — uses REST API for multi-turn conversation.
 */
public class InteractingAgent {

    private static final String TAG = "InteractingAgent";

    private static final String COLLECTION_SYSTEM_PROMPT =
            "You are TenantShield, an NYC housing inspector AI agent helping a tenant inspect their apartment.\n\n"
            + "PERSONALITY: Professional, calm, direct. Keep responses concise — 2-3 sentences max per message. "
            + "Multilingual: respond in the same language the tenant uses.\n\n"
            + "YOUR JOB RIGHT NOW: Collect the tenant's information before the inspection begins.\n\n"
            + "INFORMATION TO COLLECT:\n"
            + "1. Their full name\n"
            + "2. Their full building address including borough (Manhattan, Brooklyn, Bronx, Queens, Staten Island)\n"
            + "3. Their apartment/unit number\n"
            + "4. A description of their complaint — what is the issue?\n"
            + "5. What specifically they want inspected\n\n"
            + "FLOW:\n"
            + "- Greet the tenant warmly. Ask for their building address first.\n"
            + "- Ask one question at a time. Be conversational but efficient.\n"
            + "- If they give partial info, acknowledge what you have and ask for what's missing.\n"
            + "- Once you have ALL information, confirm what you collected.\n\n"
            + "RESPONSE FORMAT:\n"
            + "When you have ALL required information, respond with ONLY a JSON object:\n"
            + "{\"complete\": true, \"tenant_name\": \"...\", \"address\": \"...\", \"unit_number\": \"...\", "
            + "\"complaint_description\": \"...\", \"inspection_request\": \"...\", "
            + "\"response_text\": \"a brief confirmation of what you collected\"}\n\n"
            + "If information is still missing, respond with:\n"
            + "{\"complete\": false, \"response_text\": \"your question to the user\"}";

    private static final String EXPLANATION_SYSTEM_PROMPT =
            "You are TenantShield, an NYC housing inspector AI agent explaining inspection results to a tenant.\n\n"
            + "PERSONALITY: Professional, calm, empathetic. The tenant may be stressed — be reassuring.\n\n"
            + "EXPLANATION RULES:\n"
            + "- Explain the hazard classification clearly:\n"
            + "  * Class C (immediately hazardous): Must be corrected within 24 hours. Examples: water near electrical, "
            + "no heat in winter, gas leak, broken lock, lead paint with child under 6, missing smoke detector.\n"
            + "  * Class B (hazardous): Must be corrected within 30 days. Examples: large mold over 10 sq ft, "
            + "pest infestation, broken elevator in 6+ story building, broken window.\n"
            + "  * Class A (non-hazardous): Must be corrected within 90 days. Examples: small mold under 10 sq ft, "
            + "paint peeling (non-lead), minor plaster cracks.\n"
            + "- Describe what was found in plain language — 2-3 sentences per finding.\n"
            + "- Explain what actions the tenant can take as next steps.\n"
            + "- If Class C was found, emphasize urgency and mention emergency contacts (311, 911 for gas).\n"
            + "- End by reassuring them that a formal complaint is being filed with the official legal citation.\n"
            + "- Never invent legal statutes — only reference what the inspection result provides.\n\n"
            + "Respond with JSON: {\"response_text\": \"your explanation to speak to the user\"}";

    private final GeminiApiService apiService;
    private final VoiceService voiceService;
    private final List<String[]> conversationHistory;

    public interface CollectionCallback {
        void onUserInfoReady(UserInfo info);
        void onVoiceOutput(String text);
        void onError(String message);
    }

    public interface ExplanationCallback {
        void onExplanationReady(String explanationText);
        void onError(String message);
    }

    public InteractingAgent(GeminiApiService apiService, VoiceService voiceService) {
        this.apiService = apiService;
        this.voiceService = voiceService;
        this.conversationHistory = new ArrayList<>();
    }

    public void startCollection(CollectionCallback callback) {
        conversationHistory.clear();
        conversationHistory.add(new String[]{"user", "Hello, I need to file a complaint about my building."});
        sendCollectionRequest(callback);
    }

    public void processUserInput(String userText, CollectionCallback callback) {
        conversationHistory.add(new String[]{"user", userText});
        sendCollectionRequest(callback);
    }

    private void sendCollectionRequest(CollectionCallback callback) {
        apiService.generateContentWithConversation(COLLECTION_SYSTEM_PROMPT, conversationHistory, new GeminiApiService.GeminiCallback() {
            @Override
            public void onSuccess(String responseText) {
                Log.d(TAG, "Response: " + responseText.substring(0, Math.min(200, responseText.length())));
                conversationHistory.add(new String[]{"model", responseText});
                handleCollectionResponse(responseText, callback);
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Collection error: " + message);
                callback.onError(message);
            }
        });
    }

    private void handleCollectionResponse(String responseText, CollectionCallback callback) {
        try {
            String jsonText = responseText.trim();
            if (jsonText.startsWith("```json")) jsonText = jsonText.substring(7);
            if (jsonText.startsWith("```")) jsonText = jsonText.substring(3);
            if (jsonText.endsWith("```")) jsonText = jsonText.substring(0, jsonText.length() - 3);
            jsonText = jsonText.trim();

            JsonObject json = JsonParser.parseString(jsonText).getAsJsonObject();
            boolean complete = json.has("complete") && json.get("complete").getAsBoolean();
            String responseMessage = json.has("response_text")
                    ? json.get("response_text").getAsString()
                    : jsonText;

            if (complete) {
                UserInfo info = new UserInfo();
                if (json.has("tenant_name")) info.setTenantName(json.get("tenant_name").getAsString());
                if (json.has("address")) info.setAddress(json.get("address").getAsString());
                if (json.has("unit_number")) info.setUnitNumber(json.get("unit_number").getAsString());
                if (json.has("complaint_description")) info.setComplaintDescription(json.get("complaint_description").getAsString());
                if (json.has("inspection_request")) info.setInspectionRequest(json.get("inspection_request").getAsString());
                info.setCollectedAt(System.currentTimeMillis());

                callback.onVoiceOutput(responseMessage);
                callback.onUserInfoReady(info);
            } else {
                callback.onVoiceOutput(responseMessage);
            }
        } catch (Exception e) {
            Log.w(TAG, "Response is not JSON, treating as plain text: " + e.getMessage());
            callback.onVoiceOutput(responseText);
        }
    }

    public void explainResults(InspectionResult result, ExplanationCallback callback) {
        String prompt = "Here are the inspection results to explain to the tenant: " + new Gson().toJson(result);

        apiService.generateContent(EXPLANATION_SYSTEM_PROMPT, prompt, new GeminiApiService.GeminiCallback() {
            @Override
            public void onSuccess(String responseText) {
                try {
                    String jsonText = responseText.trim();
                    if (jsonText.startsWith("```json")) jsonText = jsonText.substring(7);
                    if (jsonText.startsWith("```")) jsonText = jsonText.substring(3);
                    if (jsonText.endsWith("```")) jsonText = jsonText.substring(0, jsonText.length() - 3);
                    jsonText = jsonText.trim();

                    JsonObject json = JsonParser.parseString(jsonText).getAsJsonObject();
                    String explanationText = json.get("response_text").getAsString();
                    callback.onExplanationReady(explanationText);
                } catch (Exception e) {
                    callback.onExplanationReady(responseText);
                }
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
}

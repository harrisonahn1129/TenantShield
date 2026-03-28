package com.example.tenantshield.agents.interacting;

import android.content.Context;

import com.example.tenantshield.agents.service.GeminiApiService;
import com.example.tenantshield.agents.service.GeminiLiveApiService;
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
 * Interacting Agent — uses Vertex AI Live API (WebSocket) for real-time
 * conversational interaction during info collection,
 * and REST API for result explanation.
 *
 * Falls back to REST API if Live API connection fails.
 */
public class InteractingAgent {

    private static final String TAG = "InteractingAgent";

    private static final String COLLECTION_SYSTEM_PROMPT =
            "You are a building inspection assistant for TenantShield. Your job is to collect information from a tenant filing a complaint. "
            + "You need to gather: 1) Their full name, 2) Their full address including unit number, 3) A description of their complaint, "
            + "4) What specifically they want inspected. Be conversational but efficient. Ask one question at a time. "
            + "When you have ALL required information, respond with ONLY a JSON object: "
            + "{\"complete\": true, \"tenant_name\": \"...\", \"address\": \"...\", \"unit_number\": \"...\", "
            + "\"complaint_description\": \"...\", \"inspection_request\": \"...\", "
            + "\"response_text\": \"a brief confirmation of what you collected\"}. "
            + "If information is still missing, respond with: {\"complete\": false, \"response_text\": \"your question to the user\"}";

    private static final String EXPLANATION_SYSTEM_PROMPT =
            "You are explaining building inspection results to a tenant. Be clear and empathetic. "
            + "Explain the hazard classification, what was found, and what actions are recommended. "
            + "Keep it concise - 2-3 sentences per finding. End by reassuring them that a formal complaint is being filed. "
            + "Respond with JSON: {\"response_text\": \"your explanation to speak to the user\"}";

    private final GeminiApiService restApiService;
    private final GeminiLiveApiService liveApiService;
    private final VoiceService voiceService;
    private final List<String[]> conversationHistory;
    private boolean useLiveApi = true;
    private boolean liveApiReceivedResponse = false;
    private CollectionCallback activeCallback;

    public interface CollectionCallback {
        void onUserInfoReady(UserInfo info);
        void onVoiceOutput(String text);
        void onError(String message);
    }

    public interface ExplanationCallback {
        void onExplanationReady(String explanationText);
        void onError(String message);
    }

    public InteractingAgent(GeminiApiService restApiService, VoiceService voiceService, Context context) {
        this.restApiService = restApiService;
        this.liveApiService = new GeminiLiveApiService(context);
        this.voiceService = voiceService;
        this.conversationHistory = new ArrayList<>();
    }

    /**
     * Starts the collection conversation.
     * Attempts Live API first, falls back to REST if it fails.
     */
    public void startCollection(CollectionCallback callback) {
        conversationHistory.clear();

        if (useLiveApi) {
            startCollectionWithLiveApi(callback);
        } else {
            startCollectionWithRest(callback);
        }
    }

    private void startCollectionWithLiveApi(CollectionCallback callback) {
        liveApiReceivedResponse = false;
        activeCallback = callback;

        liveApiService.connect(COLLECTION_SYSTEM_PROMPT, new GeminiLiveApiService.LiveSessionListener() {
            @Override
            public void onConnected() {
                Log.d(TAG, "Live API connected, sending initial message");
                liveApiService.sendText("Hello, I need to file a complaint about my building.");
            }

            @Override
            public void onTextResponse(String text) {
                liveApiReceivedResponse = true;
                Log.d(TAG, "Live response: " + text.substring(0, Math.min(100, text.length())));
                handleCollectionResponse(text, activeCallback);
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "Live API error: " + message + " — falling back to REST API");
                useLiveApi = false;
                startCollectionWithRest(activeCallback);
            }

            @Override
            public void onClosed() {
                Log.d(TAG, "Live session closed");
                // If closed before receiving any response, fall back to REST
                if (!liveApiReceivedResponse && activeCallback != null) {
                    Log.w(TAG, "Live API closed without response — falling back to REST API");
                    useLiveApi = false;
                    startCollectionWithRest(activeCallback);
                }
            }
        });
    }

    private void startCollectionWithRest(CollectionCallback callback) {
        conversationHistory.clear();
        conversationHistory.add(new String[]{"user", "Hello, I need to file a complaint about my building."});
        sendRestCollectionRequest(callback);
    }

    /**
     * Sends user input through the active connection (Live or REST).
     */
    public void processUserInput(String userText, CollectionCallback callback) {
        activeCallback = callback;
        if (useLiveApi && liveApiService.isSessionActive()) {
            liveApiService.sendText(userText);
        } else {
            conversationHistory.add(new String[]{"user", userText});
            sendRestCollectionRequest(callback);
        }
    }

    private void sendRestCollectionRequest(CollectionCallback callback) {
        restApiService.generateContentWithConversation(COLLECTION_SYSTEM_PROMPT, conversationHistory, new GeminiApiService.GeminiCallback() {
            @Override
            public void onSuccess(String responseText) {
                Log.d(TAG, "REST response: " + responseText.substring(0, Math.min(200, responseText.length())));
                conversationHistory.add(new String[]{"model", responseText});
                handleCollectionResponse(responseText, callback);
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "REST collection error: " + message);
                callback.onError(message);
            }
        });
    }

    /**
     * Parses the response and either extracts UserInfo or forwards the question.
     */
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

                liveApiService.disconnect();
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

    /**
     * Explains inspection results using REST API.
     */
    public void explainResults(InspectionResult result, ExplanationCallback callback) {
        String prompt = "Here are the inspection results to explain to the tenant: " + new Gson().toJson(result);

        restApiService.generateContent(EXPLANATION_SYSTEM_PROMPT, prompt, new GeminiApiService.GeminiCallback() {
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

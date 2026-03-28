package com.example.tenantshield.agents.interacting;

import com.example.tenantshield.agents.config.GeminiConfig;
import com.example.tenantshield.agents.service.GeminiApiService;
import com.example.tenantshield.agents.service.VoiceService;
import com.example.tenantshield.agents.models.UserInfo;
import com.example.tenantshield.agents.models.InspectionResult;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import android.util.Log;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Interacting Agent — uses Cloud Run proxy (ADC) for Gemini Live API conversations.
 * Falls back to direct REST API if proxy is unavailable.
 */
public class InteractingAgent {

    private static final String TAG = "InteractingAgent";
    private static final MediaType JSON = MediaType.parse("application/json");

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

    private final GeminiApiService restApiService;
    private final VoiceService voiceService;
    private final OkHttpClient httpClient;
    private String sessionId;
    private boolean useProxy = true;
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

    public InteractingAgent(GeminiApiService restApiService, VoiceService voiceService) {
        this.restApiService = restApiService;
        this.voiceService = voiceService;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Starts the collection conversation via Cloud Run proxy.
     * Falls back to direct REST if proxy is unavailable.
     */
    public void startCollection(CollectionCallback callback) {
        activeCallback = callback;
        sessionId = null;

        if (useProxy) {
            startProxySession(callback);
        } else {
            startDirectRest(callback);
        }
    }

    private void startProxySession(CollectionCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("system_prompt", COLLECTION_SYSTEM_PROMPT);

        Request request = new Request.Builder()
                .url(GeminiConfig.getProxySessionStartUrl())
                .post(RequestBody.create(JSON, body.toString()))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.w(TAG, "Proxy unavailable: " + e.getMessage() + " — falling back to REST");
                useProxy = false;
                startDirectRest(callback);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        Log.w(TAG, "Proxy error " + response.code() + " — falling back to REST");
                        useProxy = false;
                        startDirectRest(callback);
                        return;
                    }

                    JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                    sessionId = json.get("session_id").getAsString();
                    Log.d(TAG, "Proxy session started: " + sessionId);

                    // Send initial message
                    sendProxyMessage("Hello, I need to file a complaint about my building.", callback);
                } catch (Exception e) {
                    Log.w(TAG, "Proxy parse error — falling back to REST");
                    useProxy = false;
                    startDirectRest(callback);
                } finally {
                    response.close();
                }
            }
        });
    }

    private void sendProxyMessage(String message, CollectionCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("session_id", sessionId);
        body.addProperty("message", message);

        Request request = new Request.Builder()
                .url(GeminiConfig.getProxySessionSendUrl())
                .post(RequestBody.create(JSON, body.toString()))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Proxy send failed: " + e.getMessage());
                callback.onError("Connection error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        callback.onError("Server error (" + response.code() + ")");
                        return;
                    }

                    JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                    String responseText = json.get("response_text").getAsString();
                    Log.d(TAG, "Proxy response: " + responseText.substring(0, Math.min(100, responseText.length())));
                    handleCollectionResponse(responseText, callback);
                } catch (Exception e) {
                    callback.onError("Failed to parse response: " + e.getMessage());
                } finally {
                    response.close();
                }
            }
        });
    }

    /**
     * Sends user input through the active connection (proxy or REST).
     */
    public void processUserInput(String userText, CollectionCallback callback) {
        activeCallback = callback;
        if (useProxy && sessionId != null) {
            sendProxyMessage(userText, callback);
        } else {
            // Direct REST fallback uses conversation history
            List<String[]> history = new ArrayList<>();
            history.add(new String[]{"user", userText});
            restApiService.generateContentWithConversation(COLLECTION_SYSTEM_PROMPT, history, new GeminiApiService.GeminiCallback() {
                @Override
                public void onSuccess(String responseText) {
                    handleCollectionResponse(responseText, callback);
                }

                @Override
                public void onError(String message) {
                    callback.onError(message);
                }
            });
        }
    }

    private void startDirectRest(CollectionCallback callback) {
        List<String[]> history = new ArrayList<>();
        history.add(new String[]{"user", "Hello, I need to file a complaint about my building."});

        restApiService.generateContentWithConversation(COLLECTION_SYSTEM_PROMPT, history, new GeminiApiService.GeminiCallback() {
            @Override
            public void onSuccess(String responseText) {
                Log.d(TAG, "REST response: " + responseText.substring(0, Math.min(100, responseText.length())));
                handleCollectionResponse(responseText, callback);
            }

            @Override
            public void onError(String message) {
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

                // End proxy session
                if (sessionId != null) endProxySession();

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

    private void endProxySession() {
        if (sessionId == null) return;
        try {
            Request request = new Request.Builder()
                    .url(GeminiConfig.getProxySessionEndUrl() + "?session_id=" + sessionId)
                    .post(RequestBody.create(JSON, "{}"))
                    .build();
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { }

                @Override
                public void onResponse(Call call, Response response) { response.close(); }
            });
        } catch (Exception e) {
            Log.w(TAG, "Failed to end proxy session: " + e.getMessage());
        }
        sessionId = null;
    }

    /**
     * Explains inspection results — uses proxy /generate endpoint.
     */
    public void explainResults(InspectionResult result, ExplanationCallback callback) {
        String userMessage = "Here are the inspection results to explain to the tenant: " + new Gson().toJson(result);

        if (useProxy) {
            JsonObject body = new JsonObject();
            body.addProperty("system_prompt", EXPLANATION_SYSTEM_PROMPT);
            body.addProperty("user_message", userMessage);

            Request request = new Request.Builder()
                    .url(GeminiConfig.getProxyGenerateUrl())
                    .post(RequestBody.create(JSON, body.toString()))
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    // Fallback to direct REST
                    explainResultsDirect(userMessage, callback);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        if (!response.isSuccessful()) {
                            explainResultsDirect(userMessage, callback);
                            return;
                        }
                        JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                        String text = json.get("response_text").getAsString();
                        parseExplanation(text, callback);
                    } catch (Exception e) {
                        explainResultsDirect(userMessage, callback);
                    } finally {
                        response.close();
                    }
                }
            });
        } else {
            explainResultsDirect(userMessage, callback);
        }
    }

    private void explainResultsDirect(String userMessage, ExplanationCallback callback) {
        restApiService.generateContent(EXPLANATION_SYSTEM_PROMPT, userMessage, new GeminiApiService.GeminiCallback() {
            @Override
            public void onSuccess(String responseText) {
                parseExplanation(responseText, callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private void parseExplanation(String responseText, ExplanationCallback callback) {
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
}

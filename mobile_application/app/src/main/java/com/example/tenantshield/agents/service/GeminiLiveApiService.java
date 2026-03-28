package com.example.tenantshield.agents.service;

import android.content.Context;
import android.util.Log;

import com.example.tenantshield.agents.config.GeminiConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * WebSocket-based client for the Gemini Live API via Vertex AI.
 * Uses service account credentials for OAuth2 bearer token authentication.
 */
public class GeminiLiveApiService {

    private static final String TAG = "GeminiLiveApi";

    private final OkHttpClient client;
    private final VertexAuthService authService;
    private final ExecutorService executor;
    private WebSocket webSocket;
    private LiveSessionListener listener;
    private boolean sessionActive = false;
    private String systemPrompt;
    private final StringBuilder responseAccumulator = new StringBuilder();

    public interface LiveSessionListener {
        void onConnected();
        void onTextResponse(String text);
        void onError(String message);
        void onClosed();
    }

    public GeminiLiveApiService(Context context) {
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build();
        authService = new VertexAuthService(context);
        executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Opens a Vertex AI Live API session with the given system prompt.
     * Gets an access token first, then connects via WebSocket.
     */
    public void connect(String systemPrompt, LiveSessionListener listener) {
        this.listener = listener;
        this.systemPrompt = systemPrompt;

        // Get access token on background thread, then connect
        executor.execute(() -> {
            if (!authService.initialize()) {
                listener.onError("Failed to load service account credentials. " +
                        "Make sure service-account.json exists in app/src/main/assets/");
                return;
            }

            String accessToken = authService.getAccessToken();
            if (accessToken == null || accessToken.isEmpty()) {
                listener.onError("Failed to obtain access token from service account.");
                return;
            }

            Log.d(TAG, "Access token obtained, connecting to Vertex AI Live API...");

            Request request = new Request.Builder()
                    .url(GeminiConfig.getVertexLiveApiWebSocketUrl())
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .build();

            webSocket = client.newWebSocket(request, new WebSocketListener() {
                @Override
                public void onOpen(WebSocket ws, Response response) {
                    Log.d(TAG, "WebSocket connected to Vertex AI");
                    sessionActive = true;
                    sendSetupMessage(ws);
                }

                @Override
                public void onMessage(WebSocket ws, String text) {
                    handleServerMessage(text);
                }

                @Override
                public void onClosing(WebSocket ws, int code, String reason) {
                    Log.d(TAG, "WebSocket closing: " + code + " " + reason);
                    sessionActive = false;
                    ws.close(1000, null);
                }

                @Override
                public void onClosed(WebSocket ws, int code, String reason) {
                    Log.d(TAG, "WebSocket closed: " + code);
                    sessionActive = false;
                    if (listener != null) listener.onClosed();
                }

                @Override
                public void onFailure(WebSocket ws, Throwable t, Response response) {
                    Log.e(TAG, "WebSocket failure", t);
                    sessionActive = false;
                    String msg = t.getMessage() != null ? t.getMessage() : "WebSocket connection failed";
                    if (response != null) {
                        msg += " (HTTP " + response.code() + ")";
                    }
                    if (listener != null) listener.onError(msg);
                }
            });
        });
    }

    /**
     * Sends the initial setup message with Vertex AI model path and system instruction.
     */
    private void sendSetupMessage(WebSocket ws) {
        JsonObject setup = new JsonObject();
        JsonObject setupObj = new JsonObject();

        // Vertex AI model resource name
        setupObj.addProperty("model", GeminiConfig.getVertexModelName());

        // Generation config
        JsonObject genConfig = new JsonObject();
        JsonArray modalities = new JsonArray();
        modalities.add("TEXT");
        genConfig.add("response_modalities", modalities);
        setupObj.add("generation_config", genConfig);

        // System instruction
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JsonObject sysInstruction = new JsonObject();
            JsonArray parts = new JsonArray();
            JsonObject textPart = new JsonObject();
            textPart.addProperty("text", systemPrompt);
            parts.add(textPart);
            sysInstruction.add("parts", parts);
            setupObj.add("system_instruction", sysInstruction);
        }

        setup.add("setup", setupObj);

        String msg = setup.toString();
        Log.d(TAG, "Sending setup: " + msg.substring(0, Math.min(200, msg.length())));
        ws.send(msg);
    }

    /**
     * Sends a text message to the Live API session.
     */
    public void sendText(String text) {
        if (webSocket == null || !sessionActive) {
            Log.e(TAG, "Cannot send — session not active");
            if (listener != null) listener.onError("Live session not active");
            return;
        }

        JsonObject message = new JsonObject();
        JsonObject clientContent = new JsonObject();
        clientContent.addProperty("turn_complete", true);

        JsonArray turns = new JsonArray();
        JsonObject turn = new JsonObject();
        turn.addProperty("role", "user");

        JsonArray parts = new JsonArray();
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", text);
        parts.add(textPart);
        turn.add("parts", parts);
        turns.add(turn);

        clientContent.add("turns", turns);
        message.add("client_content", clientContent);

        String msg = message.toString();
        Log.d(TAG, "Sending text: " + msg.substring(0, Math.min(200, msg.length())));
        webSocket.send(msg);
    }

    /**
     * Handles messages received from the server.
     */
    private void handleServerMessage(String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();

            if (json.has("setupComplete")) {
                Log.d(TAG, "Setup complete");
                if (listener != null) listener.onConnected();
                return;
            }

            if (json.has("serverContent")) {
                JsonObject serverContent = json.getAsJsonObject("serverContent");

                if (serverContent.has("modelTurn")) {
                    JsonObject modelTurn = serverContent.getAsJsonObject("modelTurn");
                    if (modelTurn.has("parts")) {
                        JsonArray parts = modelTurn.getAsJsonArray("parts");
                        for (int i = 0; i < parts.size(); i++) {
                            JsonObject part = parts.get(i).getAsJsonObject();
                            if (part.has("text")) {
                                responseAccumulator.append(part.get("text").getAsString());
                            }
                        }
                    }
                }

                if (serverContent.has("turnComplete") && serverContent.get("turnComplete").getAsBoolean()) {
                    String fullText = responseAccumulator.toString().trim();
                    responseAccumulator.setLength(0);
                    if (listener != null && !fullText.isEmpty()) {
                        Log.d(TAG, "Turn complete: " + fullText.substring(0, Math.min(200, fullText.length())));
                        listener.onTextResponse(fullText);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling server message", e);
        }
    }

    /**
     * Disconnects the WebSocket session.
     */
    public void disconnect() {
        sessionActive = false;
        if (webSocket != null) {
            webSocket.close(1000, "Session ended");
            webSocket = null;
        }
    }

    public boolean isSessionActive() {
        return sessionActive;
    }
}

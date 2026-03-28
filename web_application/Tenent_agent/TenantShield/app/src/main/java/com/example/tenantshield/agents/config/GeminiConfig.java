package com.example.tenantshield.agents.config;

import com.example.tenantshield.BuildConfig;

public class GeminiConfig {

    // ── Google AI Studio (REST API for Inspection + Filing agents) ──
    // API key loaded from local.properties via BuildConfig
    public static final String API_KEY = BuildConfig.GEMINI_API_KEY;
    public static final String REST_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    public static final String REST_MODEL_NAME = "gemini-3-flash-preview";

    // REST endpoint for generateContent
    public static String getGenerateContentUrl() {
        return REST_BASE_URL + "/models/" + REST_MODEL_NAME + ":generateContent?key=" + API_KEY;
    }

    // ── Vertex AI (Live API for Interacting agent) ──
    public static final String VERTEX_PROJECT_ID = "crucial-bucksaw-371623";
    public static final String VERTEX_REGION = "us-central1";
    public static final String LIVE_MODEL_NAME = "gemini-2.0-flash-live-001";

    // Vertex AI Live API WebSocket endpoint
    public static String getVertexLiveApiWebSocketUrl() {
        return "wss://" + VERTEX_REGION + "-aiplatform.googleapis.com/ws/"
                + "google.cloud.aiplatform.v1beta1.LlmBidiService/BidiGenerateContent";
    }

    // Vertex AI model resource name
    public static String getVertexModelName() {
        return "projects/" + VERTEX_PROJECT_ID + "/locations/" + VERTEX_REGION
                + "/publishers/google/models/" + LIVE_MODEL_NAME;
    }
}

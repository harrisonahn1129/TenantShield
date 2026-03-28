package com.example.tenantshield.agents.config;

import com.example.tenantshield.BuildConfig;

public class GeminiConfig {

    // ── Cloud Run Proxy (ADC-based, no API keys on device) ──
    public static final String CLOUD_PROXY_URL = "https://tenantshield-proxy-479778466693.us-central1.run.app";

    // Proxy endpoints
    public static String getProxySessionStartUrl() {
        return CLOUD_PROXY_URL + "/session/start";
    }

    public static String getProxySessionSendUrl() {
        return CLOUD_PROXY_URL + "/session/send";
    }

    public static String getProxySessionEndUrl() {
        return CLOUD_PROXY_URL + "/session/end";
    }

    public static String getProxyAnalyzeUrl() {
        return CLOUD_PROXY_URL + "/analyze";
    }

    public static String getProxyGenerateUrl() {
        return CLOUD_PROXY_URL + "/generate";
    }

    // ── Fallback: Google AI Studio REST API (used if proxy is down) ──
    public static final String API_KEY = BuildConfig.GEMINI_API_KEY;
    public static final String REST_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    public static final String REST_MODEL_NAME = "gemini-2.0-flash";

    public static String getGenerateContentUrl() {
        return REST_BASE_URL + "/models/" + REST_MODEL_NAME + ":generateContent?key=" + API_KEY;
    }

    // ── Legacy Vertex AI methods (kept for GeminiLiveApiService compatibility) ──
    public static final String VERTEX_PROJECT_ID = "crucial-bucksaw-371623";
    public static final String VERTEX_REGION = "us-central1";
    public static final String LIVE_MODEL_NAME = "gemini-2.0-flash-live-001";

    public static String getVertexLiveApiWebSocketUrl() {
        return "wss://" + VERTEX_REGION + "-aiplatform.googleapis.com/ws/"
                + "google.cloud.aiplatform.v1beta1.LlmBidiService/BidiGenerateContent";
    }

    public static String getVertexModelName() {
        return "projects/" + VERTEX_PROJECT_ID + "/locations/" + VERTEX_REGION
                + "/publishers/google/models/" + LIVE_MODEL_NAME;
    }
}

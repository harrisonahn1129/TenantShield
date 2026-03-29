package com.example.tenantshield.agents.config;

import com.example.tenantshield.BuildConfig;

public class GeminiConfig {

    // Google AI Studio REST API — used by all agents
    public static final String API_KEY = BuildConfig.GEMINI_API_KEY;
    public static final String REST_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    public static final String REST_MODEL_NAME = "gemini-3-flash-preview";

    public static String getGenerateContentUrl() {
        return REST_BASE_URL + "/models/" + REST_MODEL_NAME + ":generateContent?key=" + API_KEY;
    }
}

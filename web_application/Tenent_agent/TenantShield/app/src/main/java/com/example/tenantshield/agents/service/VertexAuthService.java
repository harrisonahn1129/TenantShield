package com.example.tenantshield.agents.service;

import android.content.Context;
import android.util.Log;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.AccessToken;

import java.io.InputStream;
import java.util.Collections;

/**
 * Handles Vertex AI authentication using a service account JSON key file.
 *
 * Place your service account JSON key file in:
 *   app/src/main/assets/service-account.json
 *
 * This file should NOT be committed to source control.
 */
public class VertexAuthService {

    private static final String TAG = "VertexAuthService";
    private static final String CREDENTIALS_FILE = "service-account.json";
    private static final String SCOPE = "https://www.googleapis.com/auth/cloud-platform";

    private GoogleCredentials credentials;
    private final Context context;

    public VertexAuthService(Context context) {
        this.context = context;
    }

    /**
     * Initializes credentials from the service account JSON in assets.
     * Must be called before getAccessToken().
     */
    public boolean initialize() {
        try {
            InputStream stream = context.getAssets().open(CREDENTIALS_FILE);
            credentials = GoogleCredentials.fromStream(stream)
                    .createScoped(Collections.singletonList(SCOPE));
            Log.d(TAG, "Credentials loaded successfully");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load service account credentials. " +
                    "Make sure " + CREDENTIALS_FILE + " exists in app/src/main/assets/", e);
            return false;
        }
    }

    /**
     * Returns a valid access token, refreshing if needed.
     * This is a blocking call — run on a background thread.
     */
    public String getAccessToken() {
        if (credentials == null) {
            Log.e(TAG, "Credentials not initialized");
            return null;
        }

        try {
            credentials.refreshIfExpired();
            AccessToken token = credentials.getAccessToken();
            if (token != null) {
                return token.getTokenValue();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get access token", e);
        }
        return null;
    }
}

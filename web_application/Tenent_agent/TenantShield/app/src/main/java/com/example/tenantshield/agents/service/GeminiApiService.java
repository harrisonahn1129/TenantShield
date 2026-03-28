package com.example.tenantshield.agents.service;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import com.example.tenantshield.agents.config.GeminiConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GeminiApiService {

    private static final String TAG = "GeminiApiService";
    private static final MediaType JSON = MediaType.parse("application/json");
    private static final int MAX_IMAGE_DIMENSION = 1024;

    private final OkHttpClient client;
    private final Gson gson;

    public interface GeminiCallback {
        void onSuccess(String responseText);
        void onError(String errorMessage);
    }

    public GeminiApiService() {
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
        gson = new GsonBuilder().create();
    }

    public void generateContent(String systemPrompt, String userMessage, GeminiCallback callback) {
        if (GeminiConfig.API_KEY == null || GeminiConfig.API_KEY.isEmpty()) {
            callback.onError("API key is not configured. Please set your Gemini API key in GeminiConfig.");
            return;
        }

        JsonObject body = new JsonObject();

        // system_instruction
        JsonObject systemInstruction = new JsonObject();
        JsonArray systemParts = new JsonArray();
        JsonObject systemTextPart = new JsonObject();
        systemTextPart.addProperty("text", systemPrompt);
        systemParts.add(systemTextPart);
        systemInstruction.add("parts", systemParts);
        body.add("system_instruction", systemInstruction);

        // contents
        JsonArray contents = new JsonArray();
        JsonObject userContent = new JsonObject();
        userContent.addProperty("role", "user");
        JsonArray userParts = new JsonArray();
        JsonObject userTextPart = new JsonObject();
        userTextPart.addProperty("text", userMessage);
        userParts.add(userTextPart);
        userContent.add("parts", userParts);
        contents.add(userContent);
        body.add("contents", contents);

        // generationConfig
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("responseMimeType", "application/json");
        body.add("generationConfig", generationConfig);

        executeRequest(body.toString(), callback);
    }

    public void generateContentWithConversation(String systemPrompt, List<String[]> conversation, GeminiCallback callback) {
        if (GeminiConfig.API_KEY == null || GeminiConfig.API_KEY.isEmpty()) {
            callback.onError("API key is not configured. Please set your Gemini API key in GeminiConfig.");
            return;
        }

        JsonObject body = new JsonObject();

        // system_instruction
        JsonObject systemInstruction = new JsonObject();
        JsonArray systemParts = new JsonArray();
        JsonObject systemTextPart = new JsonObject();
        systemTextPart.addProperty("text", systemPrompt);
        systemParts.add(systemTextPart);
        systemInstruction.add("parts", systemParts);
        body.add("system_instruction", systemInstruction);

        // contents
        JsonArray contents = new JsonArray();
        for (String[] entry : conversation) {
            String role = entry[0];
            String text = entry[1];
            JsonObject content = new JsonObject();
            content.addProperty("role", role);
            JsonArray parts = new JsonArray();
            JsonObject textPart = new JsonObject();
            textPart.addProperty("text", text);
            parts.add(textPart);
            content.add("parts", parts);
            contents.add(content);
        }
        body.add("contents", contents);

        // generationConfig
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("responseMimeType", "application/json");
        body.add("generationConfig", generationConfig);

        executeRequest(body.toString(), callback);
    }

    public void generateContentWithImages(String systemPrompt, String userMessage, List<String> imagePaths, GeminiCallback callback) {
        if (GeminiConfig.API_KEY == null || GeminiConfig.API_KEY.isEmpty()) {
            callback.onError("API key is not configured. Please set your Gemini API key in GeminiConfig.");
            return;
        }

        JsonObject body = new JsonObject();

        // system_instruction
        JsonObject systemInstruction = new JsonObject();
        JsonArray systemParts = new JsonArray();
        JsonObject systemTextPart = new JsonObject();
        systemTextPart.addProperty("text", systemPrompt);
        systemParts.add(systemTextPart);
        systemInstruction.add("parts", systemParts);
        body.add("system_instruction", systemInstruction);

        // contents with text and inline images
        JsonArray contents = new JsonArray();
        JsonObject userContent = new JsonObject();
        userContent.addProperty("role", "user");

        JsonArray userParts = new JsonArray();

        // Text part
        JsonObject userTextPart = new JsonObject();
        userTextPart.addProperty("text", userMessage);
        userParts.add(userTextPart);

        // Image parts
        for (String imagePath : imagePaths) {
            String base64Data = encodeImageToBase64(imagePath);
            if (base64Data != null) {
                JsonObject inlineData = new JsonObject();
                inlineData.addProperty("mime_type", "image/jpeg");
                inlineData.addProperty("data", base64Data);

                JsonObject imagePart = new JsonObject();
                imagePart.add("inline_data", inlineData);
                userParts.add(imagePart);
            } else {
                Log.e(TAG, "Failed to encode image: " + imagePath);
            }
        }

        userContent.add("parts", userParts);
        contents.add(userContent);
        body.add("contents", contents);

        // generationConfig
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("responseMimeType", "application/json");
        body.add("generationConfig", generationConfig);

        executeRequest(body.toString(), callback);
    }

    private void executeRequest(String jsonBody, GeminiCallback callback) {
        RequestBody requestBody = RequestBody.create(JSON, jsonBody);

        Request request = new Request.Builder()
                .url(GeminiConfig.getGenerateContentUrl())
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Request failed", e);
                callback.onError("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "API error: " + response.code() + " - " + responseBody);
                        callback.onError("API error (" + response.code() + "): " + responseBody);
                        return;
                    }

                    String text = extractResponseText(responseBody);
                    callback.onSuccess(text);
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing response", e);
                    callback.onError("Error parsing response: " + e.getMessage());
                } finally {
                    response.close();
                }
            }
        });
    }

    private String extractResponseText(String responseBody) {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray candidates = json.getAsJsonArray("candidates");
            JsonObject firstCandidate = candidates.get(0).getAsJsonObject();
            JsonObject content = firstCandidate.getAsJsonObject("content");
            JsonArray parts = content.getAsJsonArray("parts");
            JsonObject firstPart = parts.get(0).getAsJsonObject();
            return firstPart.get("text").getAsString();
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse response text, returning raw body", e);
            return responseBody;
        }
    }

    private String encodeImageToBase64(String filePath) {
        try {
            File file = new File(filePath);

            // First decode with inJustDecodeBounds to get dimensions
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(filePath, options);

            int originalWidth = options.outWidth;
            int originalHeight = options.outHeight;

            // Calculate inSampleSize
            int inSampleSize = 1;
            int longestEdge = Math.max(originalWidth, originalHeight);
            if (longestEdge > MAX_IMAGE_DIMENSION) {
                while ((longestEdge / inSampleSize) > MAX_IMAGE_DIMENSION) {
                    inSampleSize *= 2;
                }
            }

            // Decode with inSampleSize
            options.inJustDecodeBounds = false;
            options.inSampleSize = inSampleSize;
            Bitmap bitmap = BitmapFactory.decodeFile(filePath, options);

            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap: " + filePath);
                return null;
            }

            // Scale down further if still exceeds max dimension
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int currentLongest = Math.max(width, height);

            if (currentLongest > MAX_IMAGE_DIMENSION) {
                float scale = (float) MAX_IMAGE_DIMENSION / currentLongest;
                int newWidth = Math.round(width * scale);
                int newHeight = Math.round(height * scale);
                Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                bitmap.recycle();
                bitmap = scaledBitmap;
            }

            // Compress to JPEG
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
            bitmap.recycle();

            byte[] bytes = baos.toByteArray();
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Error encoding image to Base64: " + filePath, e);
            return null;
        }
    }
}

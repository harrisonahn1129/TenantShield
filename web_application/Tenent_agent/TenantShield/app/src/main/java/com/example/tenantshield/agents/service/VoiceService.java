package com.example.tenantshield.agents.service;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.Locale;

public class VoiceService {

    public interface VoiceListener {
        void onSpeechResult(String text);
        void onSpeechError(int errorCode);
        void onTtsComplete();
    }

    private static final String TAG = "VoiceService";

    private final Context context;
    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private boolean ttsReady = false;
    private final Handler mainHandler;

    public VoiceService(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        initTts();
    }

    private void initTts() {
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                ttsReady = true;
            } else {
                Log.e(TAG, "TextToSpeech initialization failed with status: " + status);
            }
        });
    }

    public void speak(String text, VoiceListener listener) {
        if (!ttsReady) {
            listener.onTtsComplete();
            return;
        }

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
            }

            @Override
            public void onDone(String utteranceId) {
                mainHandler.post(listener::onTtsComplete);
            }

            @Override
            public void onError(String utteranceId) {
            }
        });

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tenantshield_tts");
    }

    public void startListening(VoiceListener listener) {
        mainHandler.post(() -> {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context);

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onResults(Bundle results) {
                    java.util.ArrayList<String> matches =
                            results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        listener.onSpeechResult(matches.get(0));
                    }
                }

                @Override
                public void onError(int error) {
                    listener.onSpeechError(error);
                }

                @Override
                public void onReadyForSpeech(Bundle params) {
                }

                @Override
                public void onBeginningOfSpeech() {
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                }

                @Override
                public void onEndOfSpeech() {
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                }
            });

            recognizer.startListening(intent);
        });
    }

    public void stopListening() {
        if (recognizer != null) {
            recognizer.stopListening();
        }
    }

    public void destroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (recognizer != null) {
            recognizer.destroy();
        }
    }
}

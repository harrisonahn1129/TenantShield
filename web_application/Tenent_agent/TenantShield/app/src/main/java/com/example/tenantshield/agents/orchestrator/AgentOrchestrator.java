package com.example.tenantshield.agents.orchestrator;

import com.example.tenantshield.agents.interacting.InteractingAgent;
import com.example.tenantshield.agents.inspection.InspectionAgent;
import com.example.tenantshield.agents.filing.FilingAgent;
import com.example.tenantshield.agents.models.UserInfo;
import com.example.tenantshield.agents.models.InspectionResult;
import com.example.tenantshield.agents.models.ComplaintForm;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Central state machine that coordinates the three agents (Interacting, Inspection, Filing).
 * Routes messages between them and manages the inspection pipeline.
 */
public class AgentOrchestrator {

    private static final String TAG = "AgentOrchestrator";

    // ── Listener interface ──────────────────────────────────────────────

    public interface OrchestratorListener {
        void onStateChanged(OrchestratorState newState);
        void onUserInfoCollected(UserInfo info);
        void onInspectionComplete(InspectionResult result);
        void onComplaintGenerated(ComplaintForm form);
        void onError(String agentId, String message);
        void onVoiceOutput(String text);
    }

    // ── Session data holder ─────────────────────────────────────────────

    public static class SessionData {
        private UserInfo userInfo;
        private List<String> imagePaths;
        private InspectionResult inspectionResult;
        private ComplaintForm complaintForm;

        public UserInfo getUserInfo() {
            return userInfo;
        }

        public void setUserInfo(UserInfo userInfo) {
            this.userInfo = userInfo;
        }

        public List<String> getImagePaths() {
            return imagePaths;
        }

        public void setImagePaths(List<String> imagePaths) {
            this.imagePaths = imagePaths;
        }

        public InspectionResult getInspectionResult() {
            return inspectionResult;
        }

        public void setInspectionResult(InspectionResult inspectionResult) {
            this.inspectionResult = inspectionResult;
        }

        public ComplaintForm getComplaintForm() {
            return complaintForm;
        }

        public void setComplaintForm(ComplaintForm complaintForm) {
            this.complaintForm = complaintForm;
        }
    }

    // ── Fields ──────────────────────────────────────────────────────────

    private OrchestratorState currentState = OrchestratorState.IDLE;
    private OrchestratorListener listener;

    private final InteractingAgent interactingAgent;
    private final InspectionAgent inspectionAgent;
    private final FilingAgent filingAgent;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private SessionData session = new SessionData();
    private OrchestratorState lastSuccessfulState;

    // ── Constructor ─────────────────────────────────────────────────────

    public AgentOrchestrator(InteractingAgent interactingAgent,
                             InspectionAgent inspectionAgent,
                             FilingAgent filingAgent) {
        this.interactingAgent = interactingAgent;
        this.inspectionAgent = inspectionAgent;
        this.filingAgent = filingAgent;
    }

    // ── Public accessors ────────────────────────────────────────────────

    public void setListener(OrchestratorListener listener) {
        this.listener = listener;
    }

    public OrchestratorState getCurrentState() {
        return currentState;
    }

    public SessionData getSession() {
        return session;
    }

    // ── Pipeline entry points ───────────────────────────────────────────

    public void startSession() {
        session = new SessionData();
        transitionTo(OrchestratorState.COLLECTING_INFO);

        interactingAgent.startCollection(new InteractingAgent.CollectionCallback() {
            @Override
            public void onUserInfoReady(UserInfo info) {
                session.setUserInfo(info);
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onUserInfoCollected(info);
                    }
                });
                transitionTo(OrchestratorState.AWAITING_IMAGES);
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onVoiceOutput(
                                "Great! Now please use the camera to capture photos of the areas you'd like inspected.");
                    }
                });
            }

            @Override
            public void onVoiceOutput(String text) {
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onVoiceOutput(text);
                    }
                });
            }

            @Override
            public void onError(String msg) {
                handleError("interacting", msg);
            }
        });
    }

    public void processUserSpeech(String text) {
        if (currentState != OrchestratorState.COLLECTING_INFO) {
            return;
        }

        interactingAgent.processUserInput(text, new InteractingAgent.CollectionCallback() {
            @Override
            public void onUserInfoReady(UserInfo info) {
                session.setUserInfo(info);
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onUserInfoCollected(info);
                    }
                });
                transitionTo(OrchestratorState.AWAITING_IMAGES);
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onVoiceOutput(
                                "Great! Now please use the camera to capture photos of the areas you'd like inspected.");
                    }
                });
            }

            @Override
            public void onVoiceOutput(String text) {
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onVoiceOutput(text);
                    }
                });
            }

            @Override
            public void onError(String msg) {
                handleError("interacting", msg);
            }
        });
    }

    public void submitImages(List<String> imagePaths) {
        session.setImagePaths(imagePaths);
        transitionTo(OrchestratorState.INSPECTING);

        inspectionAgent.analyzeImages(imagePaths, new InspectionAgent.InspectionCallback() {
            @Override
            public void onInspectionComplete(InspectionResult result) {
                session.setInspectionResult(result);
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onInspectionComplete(result);
                    }
                });
                transitionTo(OrchestratorState.EXPLAINING_RESULTS);
                explainAndFile(result);
            }

            @Override
            public void onError(String msg) {
                handleError("inspection", msg);
            }
        });
    }

    // ── Internal pipeline steps ─────────────────────────────────────────

    private void explainAndFile(InspectionResult result) {
        interactingAgent.explainResults(result, new InteractingAgent.ExplanationCallback() {
            @Override
            public void onExplanationReady(String text) {
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onVoiceOutput(text);
                    }
                });
                startFiling();
            }

            @Override
            public void onError(String msg) {
                Log.w(TAG, "Explanation error (non-critical): " + msg);
                startFiling();
            }
        });
    }

    private void startFiling() {
        transitionTo(OrchestratorState.FILING);

        filingAgent.generateComplaint(session.getUserInfo(), session.getInspectionResult(),
                new FilingAgent.FilingCallback() {
                    @Override
                    public void onComplaintReady(ComplaintForm form) {
                        session.setComplaintForm(form);
                        mainHandler.post(() -> {
                            if (listener != null) {
                                listener.onComplaintGenerated(form);
                            }
                        });
                        transitionTo(OrchestratorState.COMPLETE);
                    }

                    @Override
                    public void onError(String msg) {
                        handleError("filing", msg);
                    }
                });
    }

    // ── Retry / Cancel ──────────────────────────────────────────────────

    public void retry() {
        if (currentState != OrchestratorState.ERROR) {
            return;
        }

        if (lastSuccessfulState == null) {
            startSession();
            return;
        }

        switch (lastSuccessfulState) {
            case IDLE:
            case COLLECTING_INFO:
                startSession();
                break;
            case AWAITING_IMAGES:
                if (session.getImagePaths() != null) {
                    submitImages(session.getImagePaths());
                }
                break;
            case INSPECTING:
                submitImages(session.getImagePaths());
                break;
            case EXPLAINING_RESULTS:
                explainAndFile(session.getInspectionResult());
                break;
            case FILING:
                startFiling();
                break;
            default:
                startSession();
                break;
        }
    }

    public void cancelSession() {
        transitionTo(OrchestratorState.IDLE);
        session = new SessionData();
    }

    // ── State management ────────────────────────────────────────────────

    private void transitionTo(OrchestratorState newState) {
        if (newState == OrchestratorState.ERROR) {
            lastSuccessfulState = currentState;
        }
        currentState = newState;
        Log.d(TAG, "State: " + newState);
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onStateChanged(newState);
            }
        });
    }

    private void handleError(String agentId, String message) {
        Log.e(TAG, agentId + " error: " + message);
        transitionTo(OrchestratorState.ERROR);
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onError(agentId, message);
            }
        });
    }
}

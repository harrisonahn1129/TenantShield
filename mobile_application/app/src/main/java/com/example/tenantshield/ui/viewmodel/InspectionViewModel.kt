package com.example.tenantshield.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.example.tenantshield.agents.orchestrator.AgentOrchestrator
import com.example.tenantshield.agents.orchestrator.OrchestratorState
import com.example.tenantshield.agents.service.GeminiApiService
import com.example.tenantshield.agents.service.VoiceService
import com.example.tenantshield.agents.interacting.InteractingAgent
import com.example.tenantshield.agents.inspection.InspectionAgent
import com.example.tenantshield.agents.filing.FilingAgent
import com.example.tenantshield.agents.models.UserInfo
import com.example.tenantshield.agents.models.InspectionResult
import com.example.tenantshield.agents.models.ComplaintForm
import com.example.tenantshield.agents.firebase.FirebaseAuthService
import com.example.tenantshield.agents.firebase.FirestoreService
import com.example.tenantshield.agents.firebase.StorageService

data class ConversationMessage(
    val sender: String, // "agent" or "user"
    val text: String
)

class InspectionViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val pipelineState: OrchestratorState = OrchestratorState.IDLE,
        val userInfo: UserInfo? = null,
        val inspectionResult: InspectionResult? = null,
        val complaintForm: ComplaintForm? = null,
        val currentVoiceText: String = "",
        val isListening: Boolean = false,
        val isSpeaking: Boolean = false,
        val errorMessage: String? = null,
        val showServerBusy: Boolean = false,
        val conversationMessages: List<ConversationMessage> = emptyList(),
        val lastUserTranscript: String = "", // Last STT result, editable by user
        val awaitingUserConfirmation: Boolean = false // True when user can edit transcript
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var geminiApiService: GeminiApiService? = null
    private var voiceService: VoiceService? = null
    private var orchestrator: AgentOrchestrator? = null
    private var initialized = false

    // Firebase services
    val authService = FirebaseAuthService()
    private val firestoreService = FirestoreService()
    private val storageService = StorageService()
    private var submittedImagePaths: List<String> = emptyList()

    fun initialize(context: Context) {
        if (initialized) return

        val gemini = GeminiApiService()
        val voice = VoiceService(context)
        geminiApiService = gemini
        voiceService = voice

        val interactingAgent = InteractingAgent(gemini, voice)
        val inspectionAgent = InspectionAgent(gemini)
        val filingAgent = FilingAgent(gemini)

        val orch = AgentOrchestrator(interactingAgent, inspectionAgent, filingAgent)
        orchestrator = orch

        orch.setListener(object : AgentOrchestrator.OrchestratorListener {
            override fun onStateChanged(newState: OrchestratorState) {
                _uiState.update { it.copy(pipelineState = newState, errorMessage = null) }
            }

            override fun onUserInfoCollected(info: UserInfo) {
                _uiState.update { it.copy(userInfo = info) }
            }

            override fun onInspectionComplete(result: InspectionResult) {
                _uiState.update { it.copy(inspectionResult = result) }
            }

            override fun onComplaintGenerated(form: ComplaintForm) {
                _uiState.update { it.copy(complaintForm = form) }
                // Save to Firebase when pipeline completes
                saveInspectionToFirebase()
            }

            override fun onError(agentId: String, message: String) {
                val isOverloaded = message.contains("503") || message.contains("overloaded")
                        || message.contains("high demand") || message.contains("RESOURCE_EXHAUSTED")
                        || message.contains("unavailable", ignoreCase = true)
                if (isOverloaded) {
                    _uiState.update { it.copy(showServerBusy = true, errorMessage = null) }
                } else {
                    _uiState.update { it.copy(errorMessage = "[$agentId] $message") }
                }
            }

            override fun onVoiceOutput(text: String) {
                _uiState.update { state ->
                    state.copy(
                        currentVoiceText = text,
                        isSpeaking = true,
                        conversationMessages = state.conversationMessages + ConversationMessage("agent", text)
                    )
                }
                voice.speak(text, object : VoiceService.VoiceListener {
                    override fun onSpeechResult(text: String) {}
                    override fun onSpeechError(errorCode: Int) {}
                    override fun onTtsComplete() {
                        _uiState.update { it.copy(isSpeaking = false) }
                        if (_uiState.value.pipelineState == OrchestratorState.COLLECTING_INFO) {
                            _uiState.update { it.copy(isListening = true) }
                            voice.startListening(object : VoiceService.VoiceListener {
                                override fun onSpeechResult(text: String) {
                                    _uiState.update { it.copy(
                                        isListening = false,
                                        lastUserTranscript = text,
                                        awaitingUserConfirmation = true
                                    ) }
                                }
                                override fun onSpeechError(errorCode: Int) {
                                    _uiState.update { it.copy(
                                        isListening = false,
                                        awaitingUserConfirmation = true,
                                        lastUserTranscript = ""
                                    ) }
                                }
                                override fun onTtsComplete() {}
                            })
                        }
                    }
                })
            }
        })

        initialized = true
    }

    /** User confirms the transcript (possibly edited) and sends it to the agent */
    fun confirmUserInput(text: String) {
        _uiState.update { state ->
            state.copy(
                awaitingUserConfirmation = false,
                lastUserTranscript = "",
                conversationMessages = state.conversationMessages + ConversationMessage("user", text)
            )
        }
        orchestrator?.processUserSpeech(text)
    }

    /** User wants to re-speak instead of using the transcript */
    fun retryListening() {
        _uiState.update { it.copy(awaitingUserConfirmation = false, lastUserTranscript = "") }
        voiceService?.let { voice ->
            _uiState.update { it.copy(isListening = true) }
            voice.startListening(object : VoiceService.VoiceListener {
                override fun onSpeechResult(text: String) {
                    _uiState.update { it.copy(
                        isListening = false,
                        lastUserTranscript = text,
                        awaitingUserConfirmation = true
                    ) }
                }
                override fun onSpeechError(errorCode: Int) {
                    _uiState.update { it.copy(
                        isListening = false,
                        awaitingUserConfirmation = true,
                        lastUserTranscript = ""
                    ) }
                }
                override fun onTtsComplete() {}
            })
        }
    }

    fun startInspectionFlow() {
        _uiState.update { UiState() }
        orchestrator?.startSession()
    }

    fun submitImages(paths: List<String>) {
        submittedImagePaths = paths
        orchestrator?.submitImages(paths)
    }

    fun retry() {
        _uiState.update { it.copy(errorMessage = null) }
        orchestrator?.retry()
    }

    fun cancelSession() {
        _uiState.update { UiState() }
        orchestrator?.cancelSession()
    }

    /** Loads dummy inspection data for testing UI and writes to Firestore */
    fun loadDummyData() {
        val dummyUserInfo = UserInfo().apply {
            tenantName = "John Doe"
            address = "742 Evergreen Terrace, Bronx, NY 10451"
            unitNumber = "4B"
            complaintDescription = "Persistent moisture accumulation on the north-facing wall of the bedroom. Suspected mold growth behind the drywall near the window. Also noticed peeling paint on the ceiling near the bathroom."
            inspectionRequest = "Check for mold, water damage, and paint condition"
            collectedAt = System.currentTimeMillis()
        }

        val dummyFindings = java.util.ArrayList<com.example.tenantshield.agents.models.InspectionFinding>().apply {
            add(com.example.tenantshield.agents.models.InspectionFinding().apply {
                category = "HMC-02a mold_large"
                description = "Dark mold growth observed covering approximately 12 square feet on the north-facing bedroom wall, extending from the window frame to the ceiling corner."
                severity = com.example.tenantshield.agents.models.InspectionFinding.Severity.HIGH
                location = "Bedroom, north wall near window"
                evidence = "Visible black and green mold patches, moisture staining, bubbling paint"
            })
            add(com.example.tenantshield.agents.models.InspectionFinding().apply {
                category = "HMC-16 water_leak_no_electrical"
                description = "Active water staining on ceiling near bathroom indicating ongoing leak from above unit or roof."
                severity = com.example.tenantshield.agents.models.InspectionFinding.Severity.MODERATE
                location = "Bathroom ceiling"
                evidence = "Brown water stains, damp drywall, paint bubbling"
            })
            add(com.example.tenantshield.agents.models.InspectionFinding().apply {
                category = "HMC-15 paint_peeling"
                description = "Paint peeling and chipping on bathroom ceiling, non-lead paint based on building age (post-1978)."
                severity = com.example.tenantshield.agents.models.InspectionFinding.Severity.LOW
                location = "Bathroom ceiling"
                evidence = "Flaking paint chips, exposed plaster underneath"
            })
        }

        val dummyActions = java.util.ArrayList<String>().apply {
            add("File HPD complaint for Class B mold violation — landlord has 30 days to remediate")
            add("Request professional mold assessment to determine full extent behind drywall")
            add("Document all visible damage with dated photographs")
            add("Send certified letter to landlord citing NYC HMC §27-2017.1")
            add("Contact 311 if landlord does not respond within 30 days")
        }

        val dummyResult = InspectionResult().apply {
            hazardLevel = InspectionResult.HazardLevel.CLASS_B
            overallSeverity = "moderate"
            findings = dummyFindings
            recommendedActions = dummyActions
            analyzedImagePaths = java.util.ArrayList()
            rawAnalysis = "Inspection of Unit 4B at 742 Evergreen Terrace reveals a Class B hazardous condition. " +
                "The primary concern is widespread mold growth on the north-facing bedroom wall, covering approximately " +
                "12 square feet. This exceeds the 10 sq ft threshold for Class B classification under NYC HMC §27-2017.1. " +
                "The mold appears to be caused by persistent moisture intrusion, likely from a failing exterior envelope " +
                "or plumbing issue in the adjacent unit.\n\n" +
                "Additionally, water damage was observed on the bathroom ceiling with active staining patterns suggesting " +
                "an ongoing leak. Paint is peeling in the affected area but the building is post-1978 construction, " +
                "so lead paint is not a concern (Class A, §27-2013).\n\n" +
                "The landlord must be notified and has 30 days to correct the Class B violations. If not addressed, " +
                "the tenant should file a formal HPD complaint."
            inspectedAt = System.currentTimeMillis()
        }

        val dummyForm = ComplaintForm().apply {
            documentId = "TS-20260328-001"
            filingDate = "MARCH 28, 2026"
            address = "742 Evergreen Terrace, Bronx, NY 10451"
            tenantName = "Doe, John"
            natureOfComplaint = "Widespread mold growth exceeding 10 square feet on bedroom wall in violation of " +
                "NYC Housing Maintenance Code §27-2017.1 (Class B). Additional water damage and paint deterioration " +
                "observed on bathroom ceiling per §27-2005 and §27-2013."
            hazardClass = "CLASS B"
            hazardDescription = "Mold covering approximately 12 sq ft on north-facing bedroom wall with active " +
                "moisture intrusion. Water staining on bathroom ceiling indicating ongoing leak. Peeling paint on ceiling."
            inspectorSignature = "TenantShield AI Inspector"
            verificationToken = "TS-VRF-A7B3C9D1E5F2"
            generatedAt = System.currentTimeMillis()
            evidenceImagePaths = java.util.ArrayList()
        }

        // Update UI state
        _uiState.update {
            it.copy(
                pipelineState = com.example.tenantshield.agents.orchestrator.OrchestratorState.COMPLETE,
                userInfo = dummyUserInfo,
                inspectionResult = dummyResult,
                complaintForm = dummyForm
            )
        }

        // Save to Firestore if logged in
        val userId = authService.getUid()
        if (userId != null) {
            firestoreService.saveUserProfile(userId, dummyUserInfo, object : FirestoreService.FirestoreCallback {
                override fun onSuccess() { android.util.Log.d("InspectionVM", "Dummy profile saved") }
                override fun onError(message: String) { android.util.Log.e("InspectionVM", "Dummy profile save failed: $message") }
            })
            firestoreService.saveInspectionRecord(userId, dummyResult, dummyForm, emptyList(),
                object : FirestoreService.FirestoreCallback {
                    override fun onSuccess() { android.util.Log.d("InspectionVM", "Dummy inspection saved to Firestore") }
                    override fun onError(message: String) { android.util.Log.e("InspectionVM", "Dummy inspection save failed: $message") }
                })
        }
    }

    /** Saves the completed inspection to Firebase (user profile, images, inspection record) */
    private fun saveInspectionToFirebase() {
        val userId = authService.getUid() ?: return
        val state = _uiState.value
        val userInfo = state.userInfo ?: return
        val result = state.inspectionResult
        val form = state.complaintForm

        // 1. Save/update user profile
        firestoreService.saveUserProfile(userId, userInfo, object : FirestoreService.FirestoreCallback {
            override fun onSuccess() {
                android.util.Log.d("InspectionVM", "User profile saved")
            }
            override fun onError(message: String) {
                android.util.Log.e("InspectionVM", "Failed to save profile: $message")
            }
        })

        // 2. Upload images then save inspection record
        val inspectionId = "insp_${System.currentTimeMillis()}"
        if (submittedImagePaths.isNotEmpty()) {
            storageService.uploadMultipleImages(userId, inspectionId, submittedImagePaths,
                object : StorageService.UploadCallback {
                    override fun onSuccess(downloadUrls: List<String>) {
                        android.util.Log.d("InspectionVM", "Images uploaded: ${downloadUrls.size}")
                        saveInspectionRecord(userId, result, form, downloadUrls)
                    }
                    override fun onError(message: String) {
                        android.util.Log.e("InspectionVM", "Image upload failed: $message")
                        // Save record without image URLs
                        saveInspectionRecord(userId, result, form, emptyList())
                    }
                })
        } else {
            saveInspectionRecord(userId, result, form, emptyList())
        }
    }

    private fun saveInspectionRecord(
        userId: String,
        result: InspectionResult?,
        form: ComplaintForm?,
        imageUrls: List<String>
    ) {
        if (result == null) return
        firestoreService.saveInspectionRecord(userId, result, form, imageUrls,
            object : FirestoreService.FirestoreCallback {
                override fun onSuccess() {
                    android.util.Log.d("InspectionVM", "Inspection record saved to Firestore")
                }
                override fun onError(message: String) {
                    android.util.Log.e("InspectionVM", "Failed to save inspection: $message")
                }
            })
    }

    override fun onCleared() {
        super.onCleared()
        voiceService?.destroy()
    }
}

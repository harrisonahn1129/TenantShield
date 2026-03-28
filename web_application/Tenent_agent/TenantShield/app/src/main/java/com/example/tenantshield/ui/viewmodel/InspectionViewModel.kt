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

    fun initialize(context: Context) {
        if (initialized) return

        val gemini = GeminiApiService()
        val voice = VoiceService(context)
        geminiApiService = gemini
        voiceService = voice

        val interactingAgent = InteractingAgent(gemini, voice, context)
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
            }

            override fun onError(agentId: String, message: String) {
                _uiState.update { it.copy(errorMessage = "[$agentId] $message") }
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

    override fun onCleared() {
        super.onCleared()
        voiceService?.destroy()
    }
}

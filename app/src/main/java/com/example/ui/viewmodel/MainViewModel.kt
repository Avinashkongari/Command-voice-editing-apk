package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.ai.adapter.AdapterResult
import com.example.domain.model.AiModelInfo
import com.example.domain.model.AiProviderProfile
import com.example.domain.model.AiProviderType
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.ExportResolution
import com.example.domain.model.FrameRateOption
import com.example.domain.model.MediaAsset
import com.example.domain.model.MediaAssetType
import com.example.domain.model.VideoProject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class AiSettingsUiState(
    val editingProfileId: String = "",
    val providerName: String = "Google Gemini",
    val providerType: AiProviderType = AiProviderType.GEMINI,
    val baseUrl: String = AiProviderType.GEMINI.defaultBaseUrl,
    val apiKeyInput: String = "",
    val maskedSavedKey: String = "",
    val hasStoredKey: Boolean = false,
    val selectedModelId: String = "",
    val customChatPath: String = "/chat/completions",
    val customAuthHeader: String = "Authorization",
    val availableModels: List<AiModelInfo> = emptyList(),
    val isLoadingModels: Boolean = false,
    val isTestingConnection: Boolean = false,
    val isConnectionTested: Boolean = false,
    val statusMessage: String = "Enter API details, fetch models, and test connection.",
    val isError: Boolean = false
)

class MainViewModel(private val container: AppContainer) : ViewModel() {

    val projects: StateFlow<List<VideoProject>> = container.projectRepository
        .observeAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val aiProfiles: StateFlow<List<AiProviderProfile>> = container.aiConfigRepository
        .observeAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeAiProfile: StateFlow<AiProviderProfile?> = container.aiConfigRepository
        .observeActiveProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _aiSettingsUi = MutableStateFlow(AiSettingsUiState())
    val aiSettingsUi: StateFlow<AiSettingsUiState> = _aiSettingsUi.asStateFlow()

    init {
        viewModelScope.launch {
            val active = container.aiConfigRepository.getActiveProfile()
            if (active != null) {
                loadProfileIntoForm(active)
            }
        }
    }

    fun loadProfileIntoForm(profile: AiProviderProfile) {
        _aiSettingsUi.value = AiSettingsUiState(
            editingProfileId = profile.id,
            providerName = profile.providerName,
            providerType = profile.providerType,
            baseUrl = profile.baseUrl,
            apiKeyInput = "",
            maskedSavedKey = container.aiConfigRepository.getMaskedApiKey(profile.id),
            hasStoredKey = profile.hasStoredApiKey,
            selectedModelId = profile.selectedModelId,
            customChatPath = profile.customChatPath,
            customAuthHeader = profile.customAuthHeader,
            availableModels = profile.availableModels,
            isConnectionTested = profile.isConnectionTested,
            statusMessage = profile.lastTestMessage,
            isError = false
        )
    }

    fun startNewProviderForm(type: AiProviderType) {
        val defaultName = when (type) {
            AiProviderType.GEMINI -> "Google Gemini"
            AiProviderType.OPENAI_COMPATIBLE -> "OpenAI Compatible"
            AiProviderType.CUSTOM -> "Custom AI Endpoint"
        }
        _aiSettingsUi.value = AiSettingsUiState(
            editingProfileId = UUID.randomUUID().toString(),
            providerName = defaultName,
            providerType = type,
            baseUrl = type.defaultBaseUrl,
            statusMessage = "Enter API key and click Load Models.",
            isConnectionTested = false
        )
    }

    fun updateProviderType(type: AiProviderType) {
        _aiSettingsUi.update {
            it.copy(
                providerType = type,
                baseUrl = type.defaultBaseUrl,
                providerName = if (it.providerName.isBlank() || it.providerName in AiProviderType.entries.map { t -> t.displayName }) {
                    type.displayName
                } else it.providerName,
                availableModels = emptyList(),
                selectedModelId = "",
                isConnectionTested = false,
                isError = false
            )
        }
    }

    fun updateProviderName(v: String) = _aiSettingsUi.update { it.copy(providerName = v) }
    fun updateBaseUrl(v: String) = _aiSettingsUi.update { it.copy(baseUrl = v, isConnectionTested = false) }
    fun updateApiKeyInput(v: String) = _aiSettingsUi.update { it.copy(apiKeyInput = v, isConnectionTested = false) }
    fun updateCustomChatPath(v: String) = _aiSettingsUi.update { it.copy(customChatPath = v, isConnectionTested = false) }
    fun updateCustomAuthHeader(v: String) = _aiSettingsUi.update { it.copy(customAuthHeader = v, isConnectionTested = false) }

    fun selectModel(modelId: String) {
        val current = _aiSettingsUi.value
        _aiSettingsUi.update {
            it.copy(
                selectedModelId = modelId,
                isConnectionTested = false,
                statusMessage = "Selected $modelId. Run Test Connection to verify.",
                isError = false
            )
        }
        if (current.editingProfileId.isNotBlank()) {
            viewModelScope.launch {
                container.aiConfigRepository.updateSelectedModel(current.editingProfileId, modelId)
            }
        }
    }

    fun fetchModels() {
        viewModelScope.launch {
            val state = _aiSettingsUi.value
            val keyToUse = resolveEffectiveApiKey(state)
            if (keyToUse.isBlank()) {
                _aiSettingsUi.update {
                    it.copy(statusMessage = "Enter an API Key first to load available models.", isError = true)
                }
                return@launch
            }
            _aiSettingsUi.update { it.copy(isLoadingModels = true, isError = false, statusMessage = "Fetching models from provider...") }
            val tempProfile = buildProfileFromState(state)
            when (val res = container.aiProviderRouter.fetchModels(tempProfile, keyToUse)) {
                is AdapterResult.Success -> {
                    val models = res.data
                    val nextModel = state.selectedModelId.takeIf { sel -> models.any { it.id == sel } }
                        ?: models.firstOrNull()?.id.orEmpty()
                    _aiSettingsUi.update {
                        it.copy(
                            isLoadingModels = false,
                            availableModels = models,
                            selectedModelId = nextModel,
                            statusMessage = "Discovered ${models.size} models. Select one and Test Connection.",
                            isError = false
                        )
                    }
                }
                is AdapterResult.Error -> {
                    _aiSettingsUi.update {
                        it.copy(
                            isLoadingModels = false,
                            statusMessage = res.userMessage,
                            isError = true
                        )
                    }
                }
            }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            val state = _aiSettingsUi.value
            val keyToUse = resolveEffectiveApiKey(state)
            if (keyToUse.isBlank()) {
                _aiSettingsUi.update { it.copy(statusMessage = "API Key is required.", isError = true) }
                return@launch
            }
            if (state.selectedModelId.isBlank()) {
                _aiSettingsUi.update { it.copy(statusMessage = "Select or enter a Model ID first.", isError = true) }
                return@launch
            }
            _aiSettingsUi.update { it.copy(isTestingConnection = true, isError = false, statusMessage = "Testing connection...") }
            val profile = buildProfileFromState(state)
            when (val res = container.aiProviderRouter.testConnection(profile, keyToUse)) {
                is AdapterResult.Success -> {
                    val saved = container.aiConfigRepository.saveProfile(
                        profile = profile.copy(
                            isConnectionTested = true,
                            lastTestMessage = res.data.message,
                            lastTestedAtMs = System.currentTimeMillis()
                        ),
                        newRawApiKey = state.apiKeyInput.takeIf { it.isNotBlank() },
                        setAsActive = true
                    )
                    _aiSettingsUi.update {
                        it.copy(
                            editingProfileId = saved.id,
                            apiKeyInput = "",
                            hasStoredKey = true,
                            maskedSavedKey = container.aiConfigRepository.getMaskedApiKey(saved.id),
                            isTestingConnection = false,
                            isConnectionTested = true,
                            statusMessage = res.data.message,
                            isError = false
                        )
                    }
                }
                is AdapterResult.Error -> {
                    _aiSettingsUi.update {
                        it.copy(
                            isTestingConnection = false,
                            isConnectionTested = false,
                            statusMessage = res.userMessage,
                            isError = true
                        )
                    }
                }
            }
        }
    }

    fun saveConfiguration(onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            val state = _aiSettingsUi.value
            val keyToUse = resolveEffectiveApiKey(state)
            if (keyToUse.isBlank()) {
                _aiSettingsUi.update { it.copy(statusMessage = "Cannot save without an API Key.", isError = true) }
                return@launch
            }
            val profile = buildProfileFromState(state)
            val saved = container.aiConfigRepository.saveProfile(
                profile = profile,
                newRawApiKey = state.apiKeyInput.takeIf { it.isNotBlank() },
                setAsActive = true
            )
            _aiSettingsUi.update {
                it.copy(
                    editingProfileId = saved.id,
                    apiKeyInput = "",
                    hasStoredKey = true,
                    maskedSavedKey = container.aiConfigRepository.getMaskedApiKey(saved.id),
                    statusMessage = if (saved.isConnectionTested) {
                        "Configuration saved securely in Android Keystore."
                    } else {
                        "Saved. Run Test Connection to enable AI editing."
                    },
                    isError = false
                )
            }
            onSaved()
        }
    }

    fun deleteStoredCredential() {
        val id = _aiSettingsUi.value.editingProfileId
        if (id.isBlank()) return
        viewModelScope.launch {
            container.aiConfigRepository.deleteCredential(id)
            _aiSettingsUi.update {
                it.copy(
                    apiKeyInput = "",
                    maskedSavedKey = "",
                    hasStoredKey = false,
                    isConnectionTested = false,
                    statusMessage = "API credential removed from Android Keystore.",
                    isError = false
                )
            }
        }
    }

    fun activateProfile(profile: AiProviderProfile) {
        viewModelScope.launch {
            container.aiConfigRepository.setActiveProfile(profile.id)
            loadProfileIntoForm(profile.copy(isActive = true))
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            container.aiConfigRepository.deleteProfile(profileId)
            if (_aiSettingsUi.value.editingProfileId == profileId) {
                startNewProviderForm(AiProviderType.GEMINI)
            }
        }
    }

    private suspend fun resolveEffectiveApiKey(state: AiSettingsUiState): String {
        if (state.apiKeyInput.isNotBlank()) return state.apiKeyInput.trim()
        if (state.editingProfileId.isNotBlank()) {
            return container.aiConfigRepository.getDecryptedApiKey(state.editingProfileId).orEmpty()
        }
        return ""
    }

    private fun buildProfileFromState(state: AiSettingsUiState): AiProviderProfile = AiProviderProfile(
        id = state.editingProfileId.ifBlank { UUID.randomUUID().toString() },
        providerName = state.providerName.ifBlank { state.providerType.displayName },
        providerType = state.providerType,
        baseUrl = state.baseUrl.trim().trimEnd('/'),
        selectedModelId = state.selectedModelId.trim(),
        customChatPath = state.customChatPath.trim(),
        customAuthHeader = state.customAuthHeader.trim(),
        availableModels = state.availableModels,
        isConnectionTested = state.isConnectionTested,
        lastTestMessage = state.statusMessage,
        isActive = true,
        hasStoredApiKey = state.hasStoredKey || state.apiKeyInput.isNotBlank()
    )

    suspend fun createProject(
        title: String,
        aspectRatio: AspectRatioPreset,
        resolution: ExportResolution,
        fps: FrameRateOption,
        initialVisualUris: List<Uri>,
        initialAudioUris: List<Uri>
    ): VideoProject {
        val project = container.projectRepository.createProject(title, aspectRatio, resolution, fps)
        val importedVisuals = mutableListOf<MediaAsset>()
        val importedAudios = mutableListOf<MediaAsset>()

        for (uri in initialVisualUris) {
            val res = container.metadataHelper.importUriAsAsset(project.id, uri)
            res.getOrNull()?.let { asset ->
                container.projectRepository.addAsset(asset)
                importedVisuals.add(asset)
            }
        }

        for (uri in initialAudioUris) {
            val res = container.metadataHelper.importUriAsAsset(project.id, uri, MediaAssetType.AUDIO)
            res.getOrNull()?.let { asset ->
                val peaks = container.waveformExtractor.extractPeaks(uri)
                val withWave = asset.copy(waveformPeaks = peaks)
                container.projectRepository.addAsset(withWave)
                importedAudios.add(withWave)
            }
        }

        // Automatically lay initial audio or videos on timeline if user picked them during creation
        val engine = com.example.engine.TimelineEngine(project.timeline)
        var visualCursor = 0L
        for (asset in importedVisuals) {
            if (asset.type == MediaAssetType.VIDEO) {
                val dur = asset.durationMs.coerceAtLeast(3000L)
                engine.addAssetClip(asset, com.example.domain.model.TrackType.VIDEO_MAIN, visualCursor, visualCursor + dur)
                visualCursor += dur
            } else if (asset.type == MediaAssetType.IMAGE) {
                val dur = 5000L
                engine.addAssetClip(asset, com.example.domain.model.TrackType.IMAGE_OVERLAY, visualCursor, visualCursor + dur)
                visualCursor += dur
            }
        }
        var audioCursor = 0L
        for (audio in importedAudios) {
            val dur = audio.durationMs.coerceAtLeast(10_000L)
            engine.addAssetClip(audio, com.example.domain.model.TrackType.AUDIO_MAIN, audioCursor, audioCursor + dur)
            audioCursor += dur
        }

        val updated = project.copy(timeline = engine.currentDocument)
        container.projectRepository.saveProject(updated)
        return updated
    }

    fun renameProject(projectId: String, newTitle: String) {
        viewModelScope.launch { container.projectRepository.renameProject(projectId, newTitle) }
    }

    fun duplicateProject(projectId: String) {
        viewModelScope.launch { container.projectRepository.duplicateProject(projectId) }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch { container.projectRepository.deleteProject(projectId) }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(container) as T
        }
    }
}

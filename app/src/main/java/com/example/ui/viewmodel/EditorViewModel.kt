package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.AppContainer
import com.example.ai.adapter.AdapterResult
import com.example.ai.engine.AiPromptBuilder
import com.example.ai.engine.AiResponseParser
import com.example.ai.engine.CommandValidator
import com.example.ai.engine.ValidationResult
import com.example.ai.engine.VoiceCommandRecognizer
import com.example.ai.engine.VoiceLanguageMode
import com.example.ai.engine.VoiceRecognitionState
import com.example.domain.model.AiChatMessage
import com.example.domain.model.AiProviderProfile
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.ChatRole
import com.example.domain.model.ColorFilterPreset
import com.example.domain.model.EditHistoryRecord
import com.example.domain.model.ExportProgressState
import com.example.domain.model.ExportResolution
import com.example.domain.model.ExportSettings
import com.example.domain.model.FrameRateOption
import com.example.domain.model.Keyframe
import com.example.domain.model.MediaAsset
import com.example.domain.model.MediaAssetType
import com.example.domain.model.MotionEffectPreset
import com.example.domain.model.TextAnimationPreset
import com.example.domain.model.TimelineClip
import com.example.domain.model.TimelineDocument
import com.example.domain.model.TrackType
import com.example.domain.model.TransitionType
import com.example.domain.model.VideoProject
import com.example.engine.TimelineEngine
import com.example.media.DeviceEncoderCapabilities
import com.example.media.VoiceRecorderState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

data class EditorUiState(
    val project: VideoProject? = null,
    val timeline: TimelineDocument = TimelineDocument(),
    val playheadMs: Long = 0L,
    val isPlaying: Boolean = false,
    val timelineZoom: Float = 1f,
    val selectedClipId: String? = null,
    val selectedAssetId: String? = null,
    val highlightedClipId: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val lastOperationBanner: String? = null,
    val isAiProcessing: Boolean = false,
    val voiceLanguageMode: VoiceLanguageMode = VoiceLanguageMode.HINGLISH_AUTO,
    val pendingCommandForMissingAsset: String? = null,
    val isImportingMedia: Boolean = false,
    val importError: String? = null
)

class EditorViewModel(
    private val projectId: String,
    private val container: AppContainer,
    applicationContext: android.content.Context
) : ViewModel() {

    private val timelineEngine = TimelineEngine()
    val voiceRecognizer = VoiceCommandRecognizer(applicationContext)
    val voiceRecognitionState: StateFlow<VoiceRecognitionState> = voiceRecognizer.state
    val voiceRecorderState: StateFlow<VoiceRecorderState> = container.voiceRecorderManager.state
    val exportState: StateFlow<ExportProgressState> = container.exportEngine.exportState

    val videoPlayer: ExoPlayer = ExoPlayer.Builder(applicationContext).build().apply {
        repeatMode = Player.REPEAT_MODE_OFF
    }
    private val audioPlayer: ExoPlayer = ExoPlayer.Builder(applicationContext).build().apply {
        repeatMode = Player.REPEAT_MODE_OFF
    }

    private var loadedVideoClipId: String? = null
    private var loadedAudioClipId: String? = null
    private var playbackJob: Job? = null

    val assets: StateFlow<List<MediaAsset>> = container.projectRepository
        .observeAssets(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val editHistory: StateFlow<List<EditHistoryRecord>> = container.projectRepository
        .observeEditHistory(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeAiProfile: StateFlow<AiProviderProfile?> = container.aiConfigRepository
        .observeActiveProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _chatMessages = MutableStateFlow(
        listOf(
            AiChatMessage(
                id = "welcome",
                role = ChatRole.ASSISTANT,
                text = "Namaste! Main VoxCut AI hoon. Mic dabakar ya type karke editing instructions dein — e.g. \"Is image ko 1 minute 20 seconds se 1 minute 38 seconds tak lagao\" ya \"Audio ka volume 30% kar do\"."
            )
        )
    )
    val chatMessages: StateFlow<List<AiChatMessage>> = _chatMessages.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = container.projectRepository.getProject(projectId)
            if (loaded != null) {
                timelineEngine.load(loaded.timeline)
                _uiState.update {
                    it.copy(
                        project = loaded,
                        timeline = loaded.timeline,
                        canUndo = timelineEngine.canUndo,
                        canRedo = timelineEngine.canRedo
                    )
                }
            }
        }

        // Auto-dispatch recognized voice transcript
        viewModelScope.launch {
            voiceRecognizer.state.collect { state ->
                if (state is VoiceRecognitionState.ResultReady) {
                    val transcript = state.transcript
                    voiceRecognizer.resetState()
                    submitAiCommand(transcript, isVoice = true)
                }
            }
        }
    }

    fun selectClip(clipId: String?) {
        _uiState.update { it.copy(selectedClipId = clipId) }
    }

    fun selectAsset(assetId: String?) {
        _uiState.update { it.copy(selectedAssetId = assetId) }
    }

    fun setTimelineZoom(zoom: Float) {
        _uiState.update { it.copy(timelineZoom = zoom.coerceIn(0.4f, 4.5f)) }
    }

    fun setVoiceLanguageMode(mode: VoiceLanguageMode) {
        _uiState.update { it.copy(voiceLanguageMode = mode) }
    }

    fun toggleSnapping() {
        val next = timelineEngine.toggleSnapping()
        _uiState.update { it.copy(timeline = next) }
        persistTimeline("Toggled snapping")
    }

    fun seekTo(targetMs: Long) {
        val maxDur = _uiState.value.timeline.totalDurationMs
        val clamped = targetMs.coerceIn(0L, maxDur)
        _uiState.update { it.copy(playheadMs = clamped) }
        syncMediaPlayersAt(clamped, playWhenReady = _uiState.value.isPlaying, forceSeek = true)
    }

    fun togglePlayPause() {
        if (_uiState.value.isPlaying) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    fun startPlayback() {
        if (_uiState.value.isPlaying) return
        if (_uiState.value.playheadMs >= _uiState.value.timeline.totalDurationMs) {
            _uiState.update { it.copy(playheadMs = 0L) }
        }
        _uiState.update { it.copy(isPlaying = true) }
        syncMediaPlayersAt(_uiState.value.playheadMs, playWhenReady = true, forceSeek = true)

        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            var lastTick = System.currentTimeMillis()
            while (isActive && _uiState.value.isPlaying) {
                delay(33L)
                val now = System.currentTimeMillis()
                val dt = now - lastTick
                lastTick = now

                val nextMs = _uiState.value.playheadMs + dt
                val total = _uiState.value.timeline.totalDurationMs
                if (nextMs >= total) {
                    _uiState.update { it.copy(playheadMs = total, isPlaying = false) }
                    pausePlayers()
                    break
                } else {
                    _uiState.update { it.copy(playheadMs = nextMs) }
                    syncMediaPlayersAt(nextMs, playWhenReady = true, forceSeek = false)
                }
            }
        }
    }

    fun pausePlayback() {
        playbackJob?.cancel()
        playbackJob = null
        _uiState.update { it.copy(isPlaying = false) }
        pausePlayers()
    }

    private fun pausePlayers() {
        runCatching { videoPlayer.pause() }
        runCatching { audioPlayer.pause() }
    }

    private fun syncMediaPlayersAt(playheadMs: Long, playWhenReady: Boolean, forceSeek: Boolean) {
        val doc = _uiState.value.timeline
        val assetMap = assets.value.associateBy { it.id }

        // Sync active video clip on VIDEO_MAIN
        val videoClip = doc.clips.firstOrNull {
            it.trackType == TrackType.VIDEO_MAIN && playheadMs in it.startMs until it.endMs
        }
        val videoAsset = videoClip?.assetId?.let { assetMap[it] }
        if (videoClip != null && videoAsset != null && videoAsset.type == MediaAssetType.VIDEO) {
            val offsetInClip = ((playheadMs - videoClip.startMs) * videoClip.speed).toLong() + videoClip.sourceStartMs
            if (loadedVideoClipId != videoClip.id) {
                loadedVideoClipId = videoClip.id
                videoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(videoAsset.uriString)))
                videoPlayer.prepare()
                videoPlayer.seekTo(offsetInClip)
            } else if (forceSeek) {
                videoPlayer.seekTo(offsetInClip)
            }
            videoPlayer.volume = if (videoClip.isMuted) 0f else videoClip.volume.coerceIn(0f, 1f)
            videoPlayer.setPlaybackSpeed(videoClip.speed.coerceIn(0.25f, 4f))
            if (playWhenReady && !videoPlayer.isPlaying) videoPlayer.play()
        } else {
            if (videoPlayer.isPlaying) videoPlayer.pause()
        }

        // Sync active audio clip on AUDIO_MAIN or AUDIO_VOICEOVER
        val audioClip = doc.clips.firstOrNull {
            (it.trackType == TrackType.AUDIO_MAIN || it.trackType == TrackType.AUDIO_VOICEOVER) &&
                !it.isMuted && playheadMs in it.startMs until it.endMs
        }
        val audioAsset = audioClip?.assetId?.let { assetMap[it] }
        if (audioClip != null && audioAsset != null) {
            val offsetInClip = ((playheadMs - audioClip.startMs) * audioClip.speed).toLong() + audioClip.sourceStartMs
            if (loadedAudioClipId != audioClip.id) {
                loadedAudioClipId = audioClip.id
                audioPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(audioAsset.uriString)))
                audioPlayer.prepare()
                audioPlayer.seekTo(offsetInClip)
            } else if (forceSeek) {
                audioPlayer.seekTo(offsetInClip)
            }
            val effectiveVol = computeFadeAwareVolume(audioClip, playheadMs)
            audioPlayer.volume = effectiveVol
            audioPlayer.setPlaybackSpeed(audioClip.speed.coerceIn(0.25f, 4f))
            if (playWhenReady && !audioPlayer.isPlaying) audioPlayer.play()
        } else {
            if (audioPlayer.isPlaying) audioPlayer.pause()
        }
    }

    private fun computeFadeAwareVolume(clip: TimelineClip, playheadMs: Long): Float {
        var factor = 1f
        val rel = playheadMs - clip.startMs
        val rem = clip.endMs - playheadMs
        if (clip.fadeInMs > 0 && rel < clip.fadeInMs) {
            factor = (rel.toFloat() / clip.fadeInMs).coerceIn(0f, 1f)
        }
        if (clip.fadeOutMs > 0 && rem < clip.fadeOutMs) {
            factor = minOf(factor, (rem.toFloat() / clip.fadeOutMs).coerceIn(0f, 1f))
        }
        return (clip.volume * factor).coerceIn(0f, 1f)
    }

    // --- Media Import & Voiceover ---

    fun importMediaUris(
        uris: List<Uri>,
        forcedType: MediaAssetType? = null,
        addToTimelineAtPlayhead: Boolean = false
    ) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImportingMedia = true, importError = null) }
            var lastImported: MediaAsset? = null
            for (uri in uris) {
                val res = container.metadataHelper.importUriAsAsset(projectId, uri, forcedType)
                res.onSuccess { imported ->
                    val finalAsset = if (imported.type == MediaAssetType.AUDIO || imported.type == MediaAssetType.VOICEOVER) {
                        val peaks = container.waveformExtractor.extractPeaks(uri)
                        imported.copy(waveformPeaks = peaks)
                    } else imported
                    container.projectRepository.addAsset(finalAsset)
                    lastImported = finalAsset

                    if (addToTimelineAtPlayhead) {
                        val start = _uiState.value.playheadMs
                        val track = when (finalAsset.type) {
                            MediaAssetType.VIDEO -> TrackType.VIDEO_MAIN
                            MediaAssetType.IMAGE -> TrackType.IMAGE_OVERLAY
                            MediaAssetType.AUDIO -> TrackType.AUDIO_MAIN
                            MediaAssetType.VOICEOVER -> TrackType.AUDIO_VOICEOVER
                        }
                        val dur = when (finalAsset.type) {
                            MediaAssetType.IMAGE -> _uiState.value.timeline.defaultImageDurationMs
                            else -> finalAsset.durationMs.coerceAtLeast(3000L)
                        }
                        val exec = timelineEngine.addAssetClip(finalAsset, track, start, start + dur)
                        applyEngineResult(exec)
                    }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(importError = err.message ?: "Unsupported media format or unreadable file.")
                    }
                }
            }
            _uiState.update {
                it.copy(
                    isImportingMedia = false,
                    selectedAssetId = lastImported?.id ?: it.selectedAssetId
                )
            }

            // If AI was waiting for user to pick a missing asset, automatically continue the pending command!
            val pending = _uiState.value.pendingCommandForMissingAsset
            if (pending != null && lastImported != null) {
                _uiState.update { it.copy(pendingCommandForMissingAsset = null) }
                submitAiCommand(pending, isVoice = false)
            }
        }
    }

    fun addAssetToTimelineAtPlayhead(asset: MediaAsset) {
        val start = _uiState.value.playheadMs
        val track = when (asset.type) {
            MediaAssetType.VIDEO -> TrackType.VIDEO_MAIN
            MediaAssetType.IMAGE -> TrackType.IMAGE_OVERLAY
            MediaAssetType.AUDIO -> TrackType.AUDIO_MAIN
            MediaAssetType.VOICEOVER -> TrackType.AUDIO_VOICEOVER
        }
        val dur = when (asset.type) {
            MediaAssetType.IMAGE -> _uiState.value.timeline.defaultImageDurationMs
            else -> asset.durationMs.coerceAtLeast(3000L)
        }
        applyEngineResult(timelineEngine.addAssetClip(asset, track, start, start + dur))
    }

    fun updateAssetTags(asset: MediaAsset, newTags: String, newName: String) {
        viewModelScope.launch {
            container.projectRepository.updateAssetTags(asset, newTags, newName)
        }
    }

    fun deleteAsset(assetId: String) {
        viewModelScope.launch {
            container.projectRepository.deleteAsset(assetId)
        }
    }

    fun toggleVoiceoverRecording() {
        if (voiceRecorderState.value is VoiceRecorderState.Recording) {
            val recorded = container.voiceRecorderManager.stopAndSave()
            if (recorded != null) {
                importMediaUris(
                    uris = listOf(recorded.first),
                    forcedType = MediaAssetType.VOICEOVER,
                    addToTimelineAtPlayhead = true
                )
            }
        } else {
            pausePlayback()
            container.voiceRecorderManager.startRecording(viewModelScope)
        }
    }

    // --- Manual Editing Operations ---

    fun splitSelectedAtPlayhead() {
        val clipId = _uiState.value.selectedClipId ?: findClipAtPlayhead()?.id ?: return
        timelineEngine.splitClip(clipId, _uiState.value.playheadMs)?.let { applyEngineResult(it) }
    }

    fun deleteSelectedClip() {
        val clipId = _uiState.value.selectedClipId ?: return
        timelineEngine.deleteClip(clipId)?.let {
            _uiState.update { s -> s.copy(selectedClipId = null) }
            applyEngineResult(it)
        }
    }

    fun duplicateSelectedClip() {
        val clipId = _uiState.value.selectedClipId ?: return
        timelineEngine.duplicateClip(clipId)?.let { applyEngineResult(it) }
    }

    fun rotateSelectedClip(deltaDeg: Float = 90f) {
        val clipId = _uiState.value.selectedClipId ?: return
        timelineEngine.updateClip(clipId, "Rotated clip") {
            it.copy(rotationDeg = (it.rotationDeg + deltaDeg) % 360f)
        }?.let { applyEngineResult(it) }
    }

    fun updateSelectedClipSpeed(speed: Float) {
        val clipId = _uiState.value.selectedClipId ?: return
        val spd = speed.coerceIn(0.25f, 4f)
        timelineEngine.updateClip(clipId, "Speed set to ${String.format("%.2f", spd)}x") {
            val baseDur = (it.durationMs * it.speed).toLong().coerceAtLeast(300L)
            val newDur = (baseDur / spd).toLong().coerceAtLeast(250L)
            it.copy(speed = spd, endMs = it.startMs + newDur)
        }?.let { applyEngineResult(it) }
    }

    fun updateSelectedClipVolume(volume: Float, fadeInMs: Long? = null, fadeOutMs: Long? = null) {
        val clipId = _uiState.value.selectedClipId ?: return
        val vol = volume.coerceIn(0f, 2f)
        timelineEngine.updateClip(clipId, "Audio volume ${(vol * 100).toInt()}%") {
            it.copy(
                volume = vol,
                isMuted = vol == 0f,
                fadeInMs = fadeInMs ?: it.fadeInMs,
                fadeOutMs = fadeOutMs ?: it.fadeOutMs
            )
        }?.let { applyEngineResult(it) }
    }

    fun updateSelectedClipTiming(startMs: Long, endMs: Long) {
        val clipId = _uiState.value.selectedClipId ?: return
        timelineEngine.moveAndResizeClip(clipId, startMs, endMs)?.let { applyEngineResult(it) }
    }

    fun moveClip(clipId: String, startMs: Long, endMs: Long) {
        _uiState.update { it.copy(selectedClipId = clipId) }
        timelineEngine.moveAndResizeClip(clipId, startMs, endMs)?.let { applyEngineResult(it) }
    }

    fun updateSelectedClipEffects(
        brightness: Float? = null,
        contrast: Float? = null,
        saturation: Float? = null,
        exposure: Float? = null,
        blurRadius: Float? = null,
        sharpen: Float? = null,
        opacity: Float? = null,
        colorFilter: ColorFilterPreset? = null,
        motionEffect: MotionEffectPreset? = null
    ) {
        val clipId = _uiState.value.selectedClipId ?: return
        timelineEngine.updateClip(clipId, "Updated visual effects") { clip ->
            val e = clip.effects
            clip.copy(
                effects = e.copy(
                    brightness = brightness ?: e.brightness,
                    contrast = contrast ?: e.contrast,
                    saturation = saturation ?: e.saturation,
                    exposure = exposure ?: e.exposure,
                    blurRadius = blurRadius ?: e.blurRadius,
                    sharpen = sharpen ?: e.sharpen,
                    opacity = opacity ?: e.opacity,
                    colorFilter = colorFilter ?: e.colorFilter,
                    motionEffect = motionEffect ?: e.motionEffect
                )
            )
        }?.let { applyEngineResult(it) }
    }

    fun updateSelectedClipTransform(scale: Float? = null, x: Float? = null, y: Float? = null, crop: com.example.domain.model.CropRect? = null) {
        val clipId = _uiState.value.selectedClipId ?: return
        timelineEngine.updateClip(clipId, "Updated framing & crop") { clip ->
            clip.copy(
                scale = scale ?: clip.scale,
                positionX = x ?: clip.positionX,
                positionY = y ?: clip.positionY,
                crop = crop ?: clip.crop
            )
        }?.let { applyEngineResult(it) }
    }

    fun updateSelectedClipTransition(transition: TransitionType, durationMs: Long = 500L) {
        val clipId = _uiState.value.selectedClipId ?: return
        timelineEngine.updateClip(clipId, "Set ${transition.displayName} transition") {
            it.copy(transitionIn = transition, transitionInDurationMs = durationMs)
        }?.let { applyEngineResult(it) }
    }

    fun addKeyframeAtPlayhead() {
        val clip = _uiState.value.timeline.clips.firstOrNull { it.id == _uiState.value.selectedClipId } ?: return
        val offset = (_uiState.value.playheadMs - clip.startMs).coerceIn(0L, clip.durationMs)
        val kf = Keyframe(
            id = UUID.randomUUID().toString(),
            timeOffsetMs = offset,
            x = clip.positionX,
            y = clip.positionY,
            scale = clip.scale,
            rotationDeg = clip.rotationDeg,
            opacity = clip.effects.opacity,
            volume = clip.volume
        )
        timelineEngine.updateClip(clip.id, "Added keyframe at ${TimelineEngine.formatMs(offset)}") {
            it.copy(keyframes = (it.keyframes.filterNot { k -> kotlin.math.abs(k.timeOffsetMs - offset) < 120L } + kf).sortedBy { k -> k.timeOffsetMs })
        }?.let { applyEngineResult(it) }
    }

    fun addManualText(
        text: String,
        isSubtitle: Boolean,
        fontSizeSp: Float = if (isSubtitle) 20f else 28f,
        colorHex: Long = 0xFFFFFFFF,
        animation: TextAnimationPreset = TextAnimationPreset.FADE
    ) {
        if (text.isBlank()) return
        val start = _uiState.value.playheadMs
        applyEngineResult(
            timelineEngine.addTextClip(
                text = text.trim(),
                isSubtitle = isSubtitle,
                startMs = start,
                endMs = start + 4000L,
                fontSizeSp = fontSizeSp,
                colorHex = colorHex,
                animation = animation
            )
        )
    }

    fun updateSelectedText(text: String, fontSizeSp: Float, colorHex: Long, animation: TextAnimationPreset) {
        val clipId = _uiState.value.selectedClipId ?: return
        timelineEngine.updateClip(clipId, "Updated text overlay") { clip ->
            val cur = clip.textStyle ?: return@updateClip clip
            clip.copy(
                label = if (cur.isSubtitle) "Sub: $text" else text,
                textStyle = cur.copy(
                    text = text,
                    fontSizeSp = fontSizeSp,
                    textColorHex = colorHex,
                    animation = animation
                )
            )
        }?.let { applyEngineResult(it) }
    }

    fun createFreezeFrameFromSelected() {
        val clip = _uiState.value.timeline.clips.firstOrNull { it.id == _uiState.value.selectedClipId } ?: return
        val asset = assets.value.firstOrNull { it.id == clip.assetId } ?: return
        if (asset.type != MediaAssetType.VIDEO) return
        val sourceTime = (((_uiState.value.playheadMs - clip.startMs).coerceAtLeast(0L)) * clip.speed).toLong() + clip.sourceStartMs

        viewModelScope.launch {
            val res = container.metadataHelper.extractFreezeFrame(
                projectId = projectId,
                sourceUri = Uri.parse(asset.uriString),
                timeMs = sourceTime,
                labelPrefix = asset.displayName.substringBeforeLast('.')
            )
            res.onSuccess { frozenAsset ->
                container.projectRepository.addAsset(frozenAsset)
                val exec = timelineEngine.addAssetClip(
                    asset = frozenAsset,
                    trackType = TrackType.IMAGE_OVERLAY,
                    startMs = _uiState.value.playheadMs,
                    endMs = _uiState.value.playheadMs + 3000L
                )
                applyEngineResult(exec)
            }.onFailure { err ->
                _uiState.update { it.copy(lastOperationBanner = "Freeze frame failed: ${err.message}") }
            }
        }
    }

    fun undo() {
        timelineEngine.undo()?.let { applyEngineResult(it) }
    }

    fun redo() {
        timelineEngine.redo()?.let { applyEngineResult(it) }
    }

    // --- AI Command Execution Pipeline ---

    fun submitAiCommand(commandText: String, isVoice: Boolean = false) {
        val trimmed = commandText.trim()
        if (trimmed.isBlank()) return

        appendChatMessage(
            AiChatMessage(
                id = UUID.randomUUID().toString(),
                role = ChatRole.USER,
                text = trimmed,
                isVoiceInput = isVoice
            )
        )

        val profile = activeAiProfile.value
        if (profile == null || !profile.isConnectionTested || !profile.hasStoredApiKey) {
            appendChatMessage(
                AiChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = ChatRole.ERROR,
                    text = "AI Provider configure aur test nahi hua hai. Please AI API Settings mein jakar Base URL, API Key, aur Model test karein."
                )
            )
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isAiProcessing = true) }
            val apiKey = container.aiConfigRepository.getDecryptedApiKey(profile.id).orEmpty()
            val curState = _uiState.value
            val systemPrompt = AiPromptBuilder.buildSystemPrompt(
                timeline = curState.timeline,
                assets = assets.value,
                playheadMs = curState.playheadMs,
                selectedClipId = curState.selectedClipId,
                selectedAssetId = curState.selectedAssetId,
                aspectRatioLabel = curState.project?.aspectRatio?.label ?: "16:9"
            )

            when (val aiRes = container.aiProviderRouter.generateEditingResponse(profile, apiKey, systemPrompt, trimmed)) {
                is AdapterResult.Error -> {
                    _uiState.update { it.copy(isAiProcessing = false) }
                    appendChatMessage(
                        AiChatMessage(
                            id = UUID.randomUUID().toString(),
                            role = ChatRole.ERROR,
                            text = aiRes.userMessage
                        )
                    )
                }
                is AdapterResult.Success -> {
                    handleRawAiResponse(trimmed, aiRes.data)
                }
            }
        }
    }

    private fun handleRawAiResponse(originalCommand: String, rawJson: String) {
        val parsed = AiResponseParser.parse(rawJson)
        if (parsed.isFailure) {
            _uiState.update { it.copy(isAiProcessing = false) }
            appendChatMessage(
                AiChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = ChatRole.ERROR,
                    text = "Invalid AI response format: ${parsed.exceptionOrNull()?.message}"
                )
            )
            return
        }

        val envelope = parsed.getOrThrow()
        val cur = _uiState.value
        val validation = CommandValidator.validate(
            operations = envelope.operations,
            timeline = cur.timeline,
            assets = assets.value,
            playheadMs = cur.playheadMs,
            selectedClipId = cur.selectedClipId,
            selectedAssetId = cur.selectedAssetId
        )

        when (validation) {
            is ValidationResult.MissingAsset -> {
                _uiState.update {
                    it.copy(
                        isAiProcessing = false,
                        pendingCommandForMissingAsset = originalCommand
                    )
                }
                appendChatMessage(
                    AiChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = ChatRole.ASSISTANT,
                        text = validation.message,
                        missingAssetType = validation.assetType,
                        missingAssetPrompt = validation.message,
                        pendingUserCommand = originalCommand
                    )
                )
            }
            is ValidationResult.ClarificationNeeded -> {
                _uiState.update { it.copy(isAiProcessing = false) }
                appendChatMessage(
                    AiChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = ChatRole.ASSISTANT,
                        text = validation.question
                    )
                )
            }
            is ValidationResult.Invalid -> {
                _uiState.update { it.copy(isAiProcessing = false) }
                appendChatMessage(
                    AiChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = ChatRole.ERROR,
                        text = validation.reason
                    )
                )
            }
            is ValidationResult.Valid -> {
                if (envelope.status_preview.isNotBlank()) {
                    appendChatMessage(
                        AiChatMessage(
                            id = UUID.randomUUID().toString(),
                            role = ChatRole.SYSTEM_STATUS,
                            text = envelope.status_preview
                        )
                    )
                }

                // Execute validated structured command inside TimelineEngine
                val result = runCatching {
                    timelineEngine.executeAiOperations(
                        operations = validation.operations,
                        assets = assets.value,
                        playheadMs = cur.playheadMs
                    )
                }.getOrElse { error ->
                    _uiState.update { it.copy(isAiProcessing = false) }
                    appendChatMessage(
                        AiChatMessage(
                            id = UUID.randomUUID().toString(),
                            role = ChatRole.ERROR,
                            text = "Timeline edit apply nahi ho paya: ${error.message ?: "Unknown editing error"}. Timeline unchanged rakhi gayi."
                        )
                    )
                    return
                }

                if (!result.success) {
                    if (result.document != cur.timeline) applyEngineResult(result)
                    _uiState.update { it.copy(isAiProcessing = false) }
                    appendChatMessage(
                        AiChatMessage(
                            id = UUID.randomUUID().toString(),
                            role = ChatRole.ERROR,
                            text = "Edit complete nahi hua: ${result.summary}. Timeline engine ne successful edit confirm nahi ki."
                        )
                    )
                    return
                }

                if (result.document == cur.timeline) {
                    _uiState.update { it.copy(isAiProcessing = false) }
                    appendChatMessage(
                        AiChatMessage(
                            id = UUID.randomUUID().toString(),
                            role = ChatRole.ERROR,
                            text = "AI command se timeline mein koi change nahi hua. Isliye success nahi dikhaya gaya. Clip select karke command dobara dein."
                        )
                    )
                    return
                }

                applyEngineResult(result)
                _uiState.update { it.copy(isAiProcessing = false) }

                val confirmText = envelope.reply.ifBlank { result.summary }
                appendChatMessage(
                    AiChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = ChatRole.ASSISTANT,
                        text = confirmText,
                        operationSummary = result.summary,
                        rawCommandJson = rawJson
                    )
                )
            }
        }
    }

    private fun appendChatMessage(msg: AiChatMessage) {
        _chatMessages.update { (it + msg).takeLast(60) }
    }

    private fun applyEngineResult(result: com.example.engine.EngineExecutionResult) {
        _uiState.update {
            it.copy(
                timeline = result.document,
                canUndo = timelineEngine.canUndo,
                canRedo = timelineEngine.canRedo,
                highlightedClipId = result.highlightedClipId,
                selectedClipId = result.highlightedClipId ?: it.selectedClipId,
                lastOperationBanner = result.summary
            )
        }
        persistTimeline(result.summary)

        // Clear highlight pulse after 2.2s
        if (result.highlightedClipId != null) {
            viewModelScope.launch {
                delay(2200L)
                _uiState.update {
                    if (it.highlightedClipId == result.highlightedClipId) it.copy(highlightedClipId = null) else it
                }
            }
        }
    }

    fun saveProjectNow() {
        persistTimeline("Manual project save")
        _uiState.update { it.copy(lastOperationBanner = "Project saved locally.") }
    }

    fun updateProjectSettings(
        title: String,
        aspectRatio: AspectRatioPreset,
        resolution: ExportResolution,
        fps: FrameRateOption,
        autosaveEnabled: Boolean,
        defaultImageDurMs: Long,
        defaultTransitionDurMs: Long
    ) {
        val current = _uiState.value.project ?: return
        val updatedTimeline = _uiState.value.timeline.copy(
            defaultImageDurationMs = defaultImageDurMs.coerceIn(1000L, 60_000L),
            defaultTransitionDurationMs = defaultTransitionDurMs.coerceIn(200L, 3000L)
        )
        timelineEngine.load(updatedTimeline)
        val updatedProject = current.copy(
            title = title.trim().ifBlank { current.title },
            aspectRatio = aspectRatio,
            targetResolution = resolution,
            targetFps = fps,
            autosaveEnabled = autosaveEnabled,
            timeline = updatedTimeline
        )
        _uiState.update { it.copy(project = updatedProject, timeline = updatedTimeline) }
        viewModelScope.launch {
            container.projectRepository.saveProject(updatedProject)
        }
    }

    private fun persistTimeline(historyDescription: String) {
        val proj = _uiState.value.project ?: return
        val updated = proj.copy(timeline = _uiState.value.timeline, updatedAtMs = System.currentTimeMillis())
        _uiState.update { it.copy(project = updated) }
        viewModelScope.launch {
            if (proj.autosaveEnabled || historyDescription == "Manual project save") {
                container.projectRepository.saveProject(updated)
            }
            container.projectRepository.recordHistory(proj.id, historyDescription, updated.timeline)
        }
    }

    // --- Export System ---

    fun queryEncoderCapabilities(): DeviceEncoderCapabilities =
        container.exportEngine.queryDeviceCapabilities()

    fun startExport(settings: ExportSettings) {
        val proj = _uiState.value.project ?: return
        pausePlayback()
        container.exportEngine.startExport(proj.copy(timeline = _uiState.value.timeline), assets.value, settings)
    }

    fun cancelExport() = container.exportEngine.cancelExport()
    fun resetExportState() = container.exportEngine.resetState()

    private fun findClipAtPlayhead(): TimelineClip? {
        val pos = _uiState.value.playheadMs
        return _uiState.value.timeline.clips.firstOrNull { pos in it.startMs..it.endMs }
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
        voiceRecognizer.cancelListening()
        container.voiceRecorderManager.stopAndDiscard()
        runCatching { videoPlayer.release() }
        runCatching { audioPlayer.release() }
    }

    class Factory(
        private val projectId: String,
        private val container: AppContainer,
        private val applicationContext: android.content.Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return EditorViewModel(projectId, container, applicationContext) as T
        }
    }
}

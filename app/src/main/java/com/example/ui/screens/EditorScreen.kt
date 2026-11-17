package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Camera
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.engine.VoiceRecognitionState
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.MediaAssetType
import com.example.engine.TimelineEngine
import com.example.media.VoiceRecorderState
import com.example.ui.components.AiAssistantPanel
import com.example.ui.components.EditorInspectorTab
import com.example.ui.components.LivePreviewStage
import com.example.ui.components.ManualInspectorSheet
import com.example.ui.components.MultiTrackTimeline
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldAudio
import com.example.ui.theme.OnCyan
import com.example.ui.theme.RoseError
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletTrack
import com.example.ui.viewmodel.EditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    onOpenMediaLibrary: () -> Unit,
    onOpenAiAssistantFull: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenProjectSettings: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val assets by viewModel.assets.collectAsStateWithLifecycle()
    val activeAiProfile by viewModel.activeAiProfile.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceRecognitionState.collectAsStateWithLifecycle()
    val recorderState by viewModel.voiceRecorderState.collectAsStateWithLifecycle()

    var activeInspector by remember { mutableStateOf<EditorInspectorTab?>(null) }
    var showAiSheet by remember { mutableStateOf(false) }
    var confirmDeleteClip by remember { mutableStateOf(false) }
    var pendingMicAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val selectedClip = remember(uiState.timeline.clips, uiState.selectedClipId) {
        uiState.timeline.clips.firstOrNull { it.id == uiState.selectedClipId }
    }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pendingMicAction?.invoke()
        pendingMicAction = null
    }

    val visualPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(15)
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importMediaUris(uris, addToTimelineAtPlayhead = true)
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importMediaUris(uris, forcedType = MediaAssetType.AUDIO, addToTimelineAtPlayhead = true)
    }

    fun runWithMicPermission(action: () -> Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) action() else {
            pendingMicAction = action
            micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val isAiReady = activeAiProfile?.isConnectionTested == true && activeAiProfile?.hasStoredApiKey == true

    Scaffold(
        containerColor = StudioBackground,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Studio Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(StudioSurface)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    viewModel.pausePlayback()
                    onBack()
                }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onOpenProjectSettings() }
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = uiState.project?.title ?: "Loading...",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isAiReady) "AI: ${activeAiProfile?.selectedModelId}" else "AI: Setup Required",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isAiReady) EmeraldAudio else RoseError,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { viewModel.undo() },
                    enabled = uiState.canUndo,
                    modifier = Modifier.testTag("editor_undo_btn")
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Undo,
                        contentDescription = "Undo",
                        tint = if (uiState.canUndo) TextPrimary else TextMuted
                    )
                }
                IconButton(
                    onClick = { viewModel.redo() },
                    enabled = uiState.canRedo,
                    modifier = Modifier.testTag("editor_redo_btn")
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Redo,
                        contentDescription = "Redo",
                        tint = if (uiState.canRedo) TextPrimary else TextMuted
                    )
                }
                IconButton(
                    onClick = { viewModel.saveProjectNow() },
                    modifier = Modifier.testTag("editor_save_btn")
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = "Save Project", tint = CyanPrimary)
                }

                Button(
                    onClick = onOpenExport,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = OnCyan),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .padding(end = 4.dp)
                        .testTag("editor_export_btn")
                ) {
                    Text("Export", style = MaterialTheme.typography.labelLarge)
                }
            }

            // 2. Live Preview Stage (Video + Overlays + Text + Filters + Keyframes)
            LivePreviewStage(
                aspectRatio = uiState.project?.aspectRatio ?: AspectRatioPreset.RATIO_16_9,
                timeline = uiState.timeline,
                assets = assets,
                playheadMs = uiState.playheadMs,
                isPlaying = uiState.isPlaying,
                videoPlayer = viewModel.videoPlayer,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.38f)
            )

            // 3. Live Operation Status Banner
            AnimatedVisibility(visible = uiState.lastOperationBanner != null || uiState.importError != null || uiState.isImportingMedia || voiceState is VoiceRecognitionState.Listening) {
                Surface(
                    color = when {
                        uiState.importError != null -> RoseError.copy(alpha = 0.16f)
                        voiceState is VoiceRecognitionState.Listening -> CyanContainer
                        else -> StudioSurfaceElevated
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(0.5.dp, StudioBorder)
                ) {
                    val statusText = when (val vs = voiceState) {
                        is VoiceRecognitionState.Listening -> "Listening: ${vs.partialText.ifBlank { "Speak Hindi / English / Hinglish..." }}"
                        else -> when {
                            uiState.importError != null -> "Import failed: ${uiState.importError}"
                            uiState.isImportingMedia -> "Importing media and adding to timeline..."
                            else -> uiState.lastOperationBanner.orEmpty()
                        }
                    }
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelMedium,
                            color = when {
                                uiState.importError != null -> RoseError
                                voiceState is VoiceRecognitionState.Listening -> CyanPrimary
                                else -> EmeraldAudio
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (uiState.canUndo && voiceState !is VoiceRecognitionState.Listening) {
                            Text(
                                text = "UNDO",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberAccent,
                                modifier = Modifier
                                    .clickable { viewModel.undo() }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // 4. Transport & Playback Controls Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(StudioSurface)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = TimelineEngine.formatMs(uiState.playheadMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanPrimary
                    )
                    Text(
                        text = " / ${TimelineEngine.formatMs(uiState.timeline.totalDurationMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.seekTo(0L) }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Jump to start", tint = TextSecondary)
                    }
                    IconButton(onClick = { viewModel.seekTo(uiState.playheadMs - 5000L) }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Rounded.FastRewind, contentDescription = "Back 5s", tint = TextSecondary)
                    }
                    IconButton(
                        onClick = { viewModel.togglePlayPause() },
                        modifier = Modifier
                            .size(40.dp)
                            .background(CyanPrimary, CircleShape)
                            .testTag("editor_play_pause_btn")
                    ) {
                        Icon(
                            imageVector = if (uiState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play or Pause",
                            tint = OnCyan
                        )
                    }
                    IconButton(onClick = { viewModel.seekTo(uiState.playheadMs + 5000L) }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Rounded.FastForward, contentDescription = "Forward 5s", tint = TextSecondary)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isRecordingVo = recorderState is VoiceRecorderState.Recording
                    IconButton(
                        onClick = { runWithMicPermission { viewModel.toggleVoiceoverRecording() } },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = if (isRecordingVo) Icons.Rounded.Stop else Icons.Rounded.Mic,
                            contentDescription = "Record Voiceover",
                            tint = if (isRecordingVo) RoseError else EmeraldAudio
                        )
                    }
                    IconButton(
                        onClick = onOpenMediaLibrary,
                        modifier = Modifier.size(38.dp).testTag("editor_media_library_btn")
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = "Media Library", tint = AmberAccent)
                    }
                }
            }

            // 5. Multi-Track Interactive Timeline
            MultiTrackTimeline(
                timeline = uiState.timeline,
                assets = assets,
                playheadMs = uiState.playheadMs,
                isPlaying = uiState.isPlaying,
                zoom = uiState.timelineZoom,
                selectedClipId = uiState.selectedClipId,
                highlightedClipId = uiState.highlightedClipId,
                onSeek = { viewModel.seekTo(it) },
                onSelectClip = { viewModel.selectClip(it) },
                onMoveClip = { id, start, end -> viewModel.moveClip(id, start, end) },
                onZoomChange = { viewModel.setTimelineZoom(it) },
                onToggleSnapping = { viewModel.toggleSnapping() },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.38f)
            )

            // 6. Optional Inline Manual Inspector Sheet
            AnimatedVisibility(visible = activeInspector != null) {
                activeInspector?.let { tab ->
                    ManualInspectorSheet(
                        tab = tab,
                        selectedClip = selectedClip,
                        playheadMs = uiState.playheadMs,
                        onClose = { activeInspector = null },
                        onUpdateTiming = { s, e -> viewModel.updateSelectedClipTiming(s, e) },
                        onUpdateEffects = { b, c, sat, ex, bl, sh, op, flt, mot ->
                            viewModel.updateSelectedClipEffects(b, c, sat, ex, bl, sh, op, flt, mot)
                        },
                        onUpdateTransform = { sc, x, y, sx, sy, cr ->
                            viewModel.updateSelectedClipTransform(sc, x, y, sx, sy, cr)
                        },
                        onRotate = { deg -> viewModel.rotateSelectedClip(deg) },
                        onUpdateTransition = { tr, dur -> viewModel.updateSelectedClipTransition(tr, dur) },
                        onUpdateSpeed = { spd -> viewModel.updateSelectedClipSpeed(spd) },
                        onUpdateVolume = { vol, fi, fo -> viewModel.updateSelectedClipVolume(vol, fi, fo) },
                        onAddText = { txt, isSub, sz, col, anim -> viewModel.addManualText(txt, isSub, sz, col, anim) },
                        onUpdateText = { txt, sz, col, anim -> viewModel.updateSelectedText(txt, sz, col, anim) },
                        onAddKeyframe = { viewModel.addKeyframeAtPlayhead() },
                        modifier = Modifier.height(220.dp)
                    )
                }
            }

            // 7. Bottom CapCut-style Manual Toolbar + Glowing AI Voice Assistant Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioSurface)
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedClip == null) {
                        EditorToolButton(Icons.Rounded.AddPhotoAlternate, "Photo/Vid", CyanPrimary) {
                            visualPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        }
                        EditorToolButton(Icons.Rounded.AudioFile, "Audio", EmeraldAudio) {
                            audioPicker.launch(arrayOf("audio/*"))
                        }
                        EditorToolButton(Icons.Rounded.TextFields, "Text/Sub", VioletTrack) {
                            activeInspector = EditorInspectorTab.TEXT
                        }
                        EditorToolButton(Icons.Rounded.FolderOpen, "Library", AmberAccent) {
                            onOpenMediaLibrary()
                        }
                        EditorToolButton(Icons.Rounded.Chat, "AI Chat", CyanPrimary) {
                            showAiSheet = true
                        }
                    } else {
                        EditorToolButton(Icons.Rounded.ContentCut, "Split", CyanPrimary) {
                            viewModel.splitSelectedAtPlayhead()
                        }
                        EditorToolButton(Icons.Rounded.Schedule, "Trim", TextPrimary) {
                            activeInspector = EditorInspectorTab.TIMING
                        }
                        EditorToolButton(Icons.Rounded.Crop, "Crop/Pos", TextPrimary) {
                            activeInspector = EditorInspectorTab.TRANSFORM
                        }
                        EditorToolButton(Icons.Rounded.RotateRight, "Rotate", TextPrimary) {
                            viewModel.rotateSelectedClip(90f)
                        }
                        EditorToolButton(Icons.Rounded.Tune, "Effects", VioletTrack) {
                            activeInspector = EditorInspectorTab.EFFECTS
                        }
                        EditorToolButton(Icons.Rounded.Animation, "Transition", AmberAccent) {
                            activeInspector = EditorInspectorTab.TRANSITIONS
                        }
                        EditorToolButton(Icons.Rounded.Speed, "Speed", TextPrimary) {
                            activeInspector = EditorInspectorTab.SPEED
                        }
                        EditorToolButton(Icons.Rounded.VolumeUp, "Volume", EmeraldAudio) {
                            activeInspector = EditorInspectorTab.AUDIO
                        }
                        EditorToolButton(Icons.Rounded.Diamond, "Keyframe", AmberAccent) {
                            activeInspector = EditorInspectorTab.KEYFRAMES
                        }
                        EditorToolButton(Icons.Rounded.ContentCopy, "Duplicate", TextPrimary) {
                            viewModel.duplicateSelectedClip()
                        }
                        EditorToolButton(Icons.Rounded.Camera, "Freeze", CyanPrimary) {
                            viewModel.createFreezeFrameFromSelected()
                        }
                        if (selectedClip.textStyle != null) {
                            EditorToolButton(Icons.Rounded.TextFields, "Edit Text", VioletTrack) {
                                activeInspector = EditorInspectorTab.TEXT
                            }
                        }
                        EditorToolButton(Icons.Rounded.Delete, "Delete", RoseError) {
                            confirmDeleteClip = true
                        }
                    }
                }

                Spacer(Modifier.width(6.dp))

                // AI Chat & Voice Assistant Trigger Pill
                Surface(
                    color = if (voiceState is VoiceRecognitionState.Listening) RoseError else AmberAccent,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable {
                            // Opening the AI panel must never start microphone capture.
                            // Listening starts only from the explicit microphone button in AiAssistantPanel.
                            showAiSheet = true
                        }
                        .testTag("editor_ai_voice_fab")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (voiceState is VoiceRecognitionState.Listening) Icons.Rounded.Stop else Icons.Rounded.AutoAwesome,
                            contentDescription = "AI Voice Assistant",
                            tint = StudioVoid,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (voiceState is VoiceRecognitionState.Listening) "Stop" else "AI Voice",
                            style = MaterialTheme.typography.labelLarge,
                            color = StudioVoid,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Live AI Voice & Chat Modal Sheet (user sees timeline & preview behind or right above!)
    if (showAiSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAiSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = StudioBackground
        ) {
            Column(modifier = Modifier.height(470.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("VoxCut AI Voice Director", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    TextButton(onClick = {
                        showAiSheet = false
                        onOpenAiAssistantFull()
                    }) {
                        Text("Full Screen", color = CyanPrimary)
                    }
                }
                AiAssistantPanel(
                    messages = chatMessages,
                    activeProfile = activeAiProfile,
                    isProcessing = uiState.isAiProcessing,
                    voiceState = voiceState,
                    voiceLanguageMode = uiState.voiceLanguageMode,
                    onLanguageModeChange = { viewModel.setVoiceLanguageMode(it) },
                    onMicClick = {
                        runWithMicPermission {
                            viewModel.voiceRecognizer.startListening(uiState.voiceLanguageMode)
                        }
                    },
                    onStopListening = { viewModel.voiceRecognizer.stopListening() },
                    onSendTextCommand = { viewModel.submitAiCommand(it, isVoice = false) },
                    onPickMissingAsset = { missingType ->
                        when (missingType) {
                            MediaAssetType.IMAGE -> visualPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            MediaAssetType.VIDEO -> visualPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                            MediaAssetType.AUDIO, MediaAssetType.VOICEOVER -> audioPicker.launch(arrayOf("audio/*"))
                        }
                    },
                    onOpenAiSettings = {
                        showAiSheet = false
                        onOpenAiSettings()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (confirmDeleteClip) {
        AlertDialog(
            onDismissRequest = { confirmDeleteClip = false },
            title = { Text("Remove Clip from Timeline?") },
            text = { Text("Delete \"${selectedClip?.label}\" from the timeline? You can undo anytime.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteClip = false
                    viewModel.deleteSelectedClip()
                }) { Text("Delete", color = RoseError) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteClip = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EditorToolButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

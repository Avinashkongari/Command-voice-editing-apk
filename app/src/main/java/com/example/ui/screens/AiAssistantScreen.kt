package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.ai.engine.VoiceLanguageMode
import com.example.ai.engine.VoiceRecognitionState
import com.example.domain.model.AiChatMessage
import com.example.domain.model.AiProviderProfile
import com.example.domain.model.MediaAssetType
import com.example.ui.components.AiAssistantPanel
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
    messages: List<AiChatMessage>,
    activeProfile: AiProviderProfile?,
    isProcessing: Boolean,
    voiceState: VoiceRecognitionState,
    voiceLanguageMode: VoiceLanguageMode,
    onBack: () -> Unit,
    onLanguageModeChange: (VoiceLanguageMode) -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onSendTextCommand: (String) -> Unit,
    onImportVisualUris: (List<android.net.Uri>) -> Unit,
    onImportAudioUris: (List<android.net.Uri>) -> Unit,
    onOpenAiSettings: () -> Unit
) {
    val context = LocalContext.current

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onStartListening()
    }

    val visualPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris ->
        if (uris.isNotEmpty()) onImportVisualUris(uris)
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) onImportAudioUris(uris)
    }

    Scaffold(
        containerColor = StudioBackground,
        topBar = {
            TopAppBar(
                title = { Text("AI Voice Assistant") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to Editor")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenAiSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "AI API Settings", tint = CyanPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = StudioSurface,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { innerPadding ->
        AiAssistantPanel(
            messages = messages,
            activeProfile = activeProfile,
            isProcessing = isProcessing,
            voiceState = voiceState,
            voiceLanguageMode = voiceLanguageMode,
            onLanguageModeChange = onLanguageModeChange,
            onMicClick = {
                val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (hasPerm) onStartListening() else micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onStopListening = onStopListening,
            onSendTextCommand = onSendTextCommand,
            onPickMissingAsset = { missingType ->
                when (missingType) {
                    MediaAssetType.IMAGE -> visualPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    MediaAssetType.VIDEO -> visualPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    MediaAssetType.AUDIO, MediaAssetType.VOICEOVER -> audioPicker.launch(arrayOf("audio/*"))
                }
            },
            onOpenAiSettings = onOpenAiSettings,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

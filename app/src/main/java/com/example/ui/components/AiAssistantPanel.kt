package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.engine.VoiceLanguageMode
import com.example.ai.engine.VoiceRecognitionState
import com.example.domain.model.AiChatMessage
import com.example.domain.model.AiProviderProfile
import com.example.domain.model.ChatRole
import com.example.domain.model.MediaAssetType
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldAudio
import com.example.ui.theme.OnCyan
import com.example.ui.theme.RoseContainer
import com.example.ui.theme.RoseError
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AiAssistantPanel(
    messages: List<AiChatMessage>,
    activeProfile: AiProviderProfile?,
    isProcessing: Boolean,
    voiceState: VoiceRecognitionState,
    voiceLanguageMode: VoiceLanguageMode,
    onLanguageModeChange: (VoiceLanguageMode) -> Unit,
    onMicClick: () -> Unit,
    onStopListening: () -> Unit,
    onSendTextCommand: (String) -> Unit,
    onPickMissingAsset: (MediaAssetType) -> Unit,
    onOpenAiSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isAiEnabled = activeProfile?.isConnectionTested == true && activeProfile.hasStoredApiKey

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(StudioBackground)
    ) {
        // AI Connection Gating Banner
        if (!isAiEnabled) {
            Surface(
                color = RoseContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI Provider Not Tested",
                            style = MaterialTheme.typography.titleMedium,
                            color = RoseError
                        )
                        Text(
                            text = "Configure Base URL, API Key & Test Connection to unlock Voice Editing.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onOpenAiSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = RoseError, contentColor = StudioVoid)
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Setup")
                    }
                }
            }
        } else {
            // Active model & language chips bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioSurface)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(EmeraldAudio, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${activeProfile?.providerName} • ${activeProfile?.selectedModelId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldAudio
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    VoiceLanguageMode.entries.forEach { mode ->
                        val active = mode == voiceLanguageMode
                        Surface(
                            color = if (active) CyanContainer else StudioSurfaceElevated,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onLanguageModeChange(mode) }
                        ) {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) CyanPrimary else TextMuted,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Chat / Operation Log
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            items(messages, key = { it.id }) { msg ->
                ChatMessageCard(msg = msg, onPickMissingAsset = onPickMissingAsset)
            }
            if (isProcessing) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = CyanPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Interpreting voice command & validating timeline edit...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CyanPrimary
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(4.dp)) }
        }

        // Live Voice Listening Overlay Bar
        AnimatedVisibility(visible = voiceState is VoiceRecognitionState.Listening) {
            val listening = voiceState as? VoiceRecognitionState.Listening
            val pulse by rememberInfiniteTransition(label = "micPulse").animateFloat(
                initialValue = 1f,
                targetValue = 1.18f,
                animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                label = "pulseScale"
            )
            Surface(
                color = CyanContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .scale(pulse)
                            .background(CyanPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = null, tint = OnCyan)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sun raha hoon... (Speak Hindi / English / Hinglish)",
                            style = MaterialTheme.typography.labelLarge,
                            color = CyanPrimary
                        )
                        Text(
                            text = listening?.partialText?.ifBlank { "Listening..." } ?: "Listening...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                    IconButton(onClick = onStopListening) {
                        Icon(Icons.Rounded.Stop, contentDescription = "Stop listening", tint = RoseError)
                    }
                }
            }
        }

        if (voiceState is VoiceRecognitionState.Error) {
            Text(
                text = voiceState.message,
                style = MaterialTheme.typography.bodyMedium,
                color = AmberAccent,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }

        // Quick Voice/Text Sample Chips
        val quickSamples = listOf(
            "Is image ko 1 minute 20 seconds se 1 minute 38 seconds tak lagao",
            "Is photo par slow zoom effect lagao",
            "Yahan smooth transition add karo",
            "Is video ko 2x speed par chalao",
            "Audio ka volume 30 percent kar do",
            "Is scene mein subtitles add karo",
            "Last edit undo karo"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quickSamples.forEach { sample ->
                Surface(
                    color = StudioSurfaceElevated,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, StudioBorder, RoundedCornerShape(20.dp))
                        .clickable(enabled = isAiEnabled && !isProcessing) {
                            onSendTextCommand(sample)
                        }
                ) {
                    Text(
                        text = sample,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Bottom Input + Voice Mic Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioSurface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Speak or type edit command...", color = TextMuted) },
                enabled = isAiEnabled && !isProcessing,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanPrimary,
                    unfocusedBorderColor = StudioBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_command_input")
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    val cmd = inputText.trim()
                    if (cmd.isNotEmpty()) {
                        inputText = ""
                        onSendTextCommand(cmd)
                    }
                },
                enabled = isAiEnabled && !isProcessing && inputText.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (inputText.isNotBlank() && isAiEnabled) CyanPrimary else StudioSurfaceElevated,
                        CircleShape
                    )
                    .testTag("ai_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Send AI command",
                    tint = if (inputText.isNotBlank() && isAiEnabled) OnCyan else TextMuted
                )
            }
            Spacer(Modifier.width(6.dp))
            IconButton(
                onClick = {
                    if (voiceState is VoiceRecognitionState.Listening) onStopListening() else onMicClick()
                },
                enabled = isAiEnabled && !isProcessing,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (voiceState is VoiceRecognitionState.Listening) RoseError else AmberAccent,
                        CircleShape
                    )
                    .testTag("ai_mic_button")
            ) {
                Icon(
                    imageVector = if (voiceState is VoiceRecognitionState.Listening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                    contentDescription = "Voice Command Mic",
                    tint = StudioVoid
                )
            }
        }
    }
}

@Composable
private fun ChatMessageCard(
    msg: AiChatMessage,
    onPickMissingAsset: (MediaAssetType) -> Unit
) {
    var showRawJson by remember { mutableStateOf(false) }

    val (bgColor, borderColor, badgeText) = when (msg.role) {
        ChatRole.USER -> Triple(StudioSurfaceElevated, StudioBorder, if (msg.isVoiceInput) "Voice Command" else "You")
        ChatRole.ASSISTANT -> Triple(StudioSurface, CyanPrimary.copy(alpha = 0.35f), "VoxCut AI")
        ChatRole.SYSTEM_STATUS -> Triple(CyanContainer.copy(alpha = 0.45f), CyanPrimary, "Engine Status")
        ChatRole.ERROR -> Triple(RoseContainer, RoseError, "Error")
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (msg.role) {
                            ChatRole.ERROR -> Icons.Rounded.ErrorOutline
                            ChatRole.ASSISTANT -> Icons.Rounded.AutoAwesome
                            ChatRole.SYSTEM_STATUS -> Icons.Rounded.CheckCircle
                            ChatRole.USER -> Icons.Rounded.Mic
                        },
                        contentDescription = null,
                        tint = when (msg.role) {
                            ChatRole.ERROR -> RoseError
                            ChatRole.SYSTEM_STATUS, ChatRole.ASSISTANT -> CyanPrimary
                            ChatRole.USER -> AmberAccent
                        },
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (!msg.rawCommandJson.isNullOrBlank()) {
                    IconButton(
                        onClick = { showRawJson = !showRawJson },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Code,
                            contentDescription = "Inspect command JSON",
                            tint = TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = msg.text,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )

            if (!msg.operationSummary.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    color = EmeraldContainerCompat,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Executed: ${msg.operationSummary}",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldAudio,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (msg.missingAssetType != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onPickMissingAsset(msg.missingAssetType) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val icon = when (msg.missingAssetType) {
                        MediaAssetType.IMAGE -> Icons.Rounded.AddPhotoAlternate
                        MediaAssetType.VIDEO -> Icons.Rounded.VideoLibrary
                        MediaAssetType.AUDIO, MediaAssetType.VOICEOVER -> Icons.Rounded.AudioFile
                    }
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Select ${msg.missingAssetType.name.lowercase()} from Device")
                }
            }

            if (showRawJson && !msg.rawCommandJson.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = msg.rawCommandJson,
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberAccent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StudioVoid, RoundedCornerShape(6.dp))
                        .padding(8.dp)
                )
            }
        }
    }
}

private val EmeraldContainerCompat = Color(0xFF0A3826)

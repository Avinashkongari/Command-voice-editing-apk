package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.model.MediaAsset
import com.example.domain.model.MediaAssetType
import com.example.engine.TimelineEngine
import com.example.media.VoiceRecorderState
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldAudio
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.OnCyan
import com.example.ui.theme.RoseContainer
import com.example.ui.theme.RoseError
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaLibraryScreen(
    assets: List<MediaAsset>,
    selectedAssetId: String?,
    isImporting: Boolean,
    importError: String?,
    voiceRecorderState: VoiceRecorderState,
    onBack: () -> Unit,
    onImportVisualUris: (List<android.net.Uri>) -> Unit,
    onImportAudioUris: (List<android.net.Uri>) -> Unit,
    onToggleVoiceRecord: () -> Unit,
    onSelectAssetForAi: (String) -> Unit,
    onAddAssetToTimeline: (MediaAsset) -> Unit,
    onUpdateAssetTags: (MediaAsset, String, String) -> Unit,
    onDeleteAsset: (String) -> Unit
) {
    val context = LocalContext.current
    var filterType by remember { mutableStateOf<MediaAssetType?>(null) }
    var editingAsset by remember { mutableStateOf<MediaAsset?>(null) }
    var editName by remember { mutableStateOf("") }
    var editTags by remember { mutableStateOf("") }
    var permMessage by remember { mutableStateOf<String?>(null) }

    val visualPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(25)
    ) { uris ->
        if (uris.isNotEmpty()) onImportVisualUris(uris)
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) onImportAudioUris(uris)
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onToggleVoiceRecord()
        } else {
            permMessage = "Microphone permission is required to record a voiceover."
        }
    }

    val filteredAssets = remember(assets, filterType) {
        if (filterType == null) assets
        else assets.filter {
            it.type == filterType || (filterType == MediaAssetType.AUDIO && it.type == MediaAssetType.VOICEOVER)
        }
    }

    Scaffold(
        containerColor = StudioBackground,
        topBar = {
            TopAppBar(
                title = { Text("Project Media Library (${assets.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = StudioSurface,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(10.dp))

            // Import Action Buttons (Photo Picker + SAF Audio + Voiceover Recorder)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        visualPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = OnCyan),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("media_import_visual_btn")
                ) {
                    Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Photos/Video")
                }

                OutlinedButton(
                    onClick = { audioPicker.launch(arrayOf("audio/*")) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("media_import_audio_btn")
                ) {
                    Icon(Icons.Rounded.AudioFile, contentDescription = null, tint = EmeraldAudio, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Audio / BGM", color = TextPrimary)
                }

                val isRecording = voiceRecorderState is VoiceRecorderState.Recording
                Button(
                    onClick = {
                        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        if (hasPerm) onToggleVoiceRecord()
                        else recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) RoseError else StudioSurfaceElevated,
                        contentColor = if (isRecording) StudioBackground else AmberAccent
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("media_voiceover_record_btn")
                ) {
                    Icon(
                        if (isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                        contentDescription = "Record Voiceover",
                        modifier = Modifier.size(18.dp)
                    )
                    if (isRecording) {
                        Spacer(Modifier.width(4.dp))
                        val elapsed = (voiceRecorderState as VoiceRecorderState.Recording).elapsedMs
                        Text(TimelineEngine.formatMs(elapsed))
                    }
                }
            }

            if (isImporting) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = CyanPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Extracting metadata & audio waveform...", style = MaterialTheme.typography.bodyMedium, color = CyanPrimary)
                }
            }

            (importError ?: permMessage)?.let { err ->
                Spacer(Modifier.height(8.dp))
                Surface(color = RoseContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(err, color = RoseError, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(10.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            // Type Filter Chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip("All", filterType == null) { filterType = null }
                FilterChip("Images", filterType == MediaAssetType.IMAGE) { filterType = MediaAssetType.IMAGE }
                FilterChip("Videos", filterType == MediaAssetType.VIDEO) { filterType = MediaAssetType.VIDEO }
                FilterChip("Audio", filterType == MediaAssetType.AUDIO) { filterType = MediaAssetType.AUDIO }
            }

            Spacer(Modifier.height(10.dp))

            if (filteredAssets.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_empty_studio),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("No media imported yet", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        "Import photos, videos, narration audio, or record a voiceover above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredAssets, key = { it.id }) { asset ->
                        val isSelectedForAi = asset.id == selectedAssetId
                        Surface(
                            color = if (isSelectedForAi) CyanContainer.copy(alpha = 0.45f) else StudioSurface,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    1.dp,
                                    if (isSelectedForAi) CyanPrimary else StudioBorder,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { onSelectAssetForAi(asset.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Thumbnail
                                Box(
                                    modifier = Modifier
                                        .size(62.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(StudioSurfaceElevated),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (asset.thumbnailPath != null && File(asset.thumbnailPath).exists()) {
                                        AsyncImage(
                                            model = File(asset.thumbnailPath),
                                            contentDescription = asset.displayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else if (asset.type == MediaAssetType.IMAGE) {
                                        AsyncImage(
                                            model = android.net.Uri.parse(asset.uriString),
                                            contentDescription = asset.displayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (asset.type == MediaAssetType.VIDEO) Icons.Rounded.VideoLibrary else Icons.Rounded.GraphicEq,
                                            contentDescription = null,
                                            tint = if (asset.type == MediaAssetType.VIDEO) CyanPrimary else EmeraldAudio,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = asset.displayName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (isSelectedForAi) {
                                            Spacer(Modifier.width(6.dp))
                                            Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected for AI", tint = CyanPrimary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    val meta = buildString {
                                        append(asset.type.name)
                                        if (asset.durationMs > 0) append(" • ${TimelineEngine.formatMs(asset.durationMs)}")
                                        if (asset.width > 0) append(" • ${asset.width}×${asset.height}")
                                    }
                                    Text(meta, style = MaterialTheme.typography.labelSmall, color = AmberAccent)
                                    if (asset.tags.isNotBlank()) {
                                        Text(
                                            text = "AI Tags: ${asset.tags}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextSecondary
                                        )
                                    }
                                }

                                IconButton(onClick = {
                                    editingAsset = asset
                                    editName = asset.displayName
                                    editTags = asset.tags
                                }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Tag asset", tint = TextSecondary)
                                }
                                IconButton(onClick = {
                                    onAddAssetToTimeline(asset)
                                    onBack()
                                }) {
                                    Icon(Icons.Rounded.Add, contentDescription = "Add to timeline", tint = CyanPrimary)
                                }
                                IconButton(onClick = { onDeleteAsset(asset.id) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Remove from project", tint = RoseError)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingAsset != null) {
        AlertDialog(
            onDismissRequest = { editingAsset = null },
            title = { Text("Rename & Tag Asset for AI") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Display Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editTags,
                        onValueChange = { editTags = it },
                        label = { Text("AI Tags (e.g. blue character, scene 1)") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    editingAsset?.let { onUpdateAssetTags(it, editTags, editName) }
                    editingAsset = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingAsset = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) CyanContainer else StudioSurface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (selected) CyanPrimary else StudioBorder, RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) CyanPrimary else TextSecondary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

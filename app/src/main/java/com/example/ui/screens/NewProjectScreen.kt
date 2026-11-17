package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.MovieCreation
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.ExportResolution
import com.example.domain.model.FrameRateOption
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldAudio
import com.example.ui.theme.OnCyan
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectScreen(
    onBack: () -> Unit,
    onCreateProject: suspend (
        title: String,
        aspectRatio: AspectRatioPreset,
        resolution: ExportResolution,
        fps: FrameRateOption,
        visualUris: List<Uri>,
        audioUris: List<Uri>
    ) -> String
) {
    var title by remember { mutableStateOf("Story Edit ${System.currentTimeMillis() % 1000}") }
    var aspectRatio by remember { mutableStateOf(AspectRatioPreset.RATIO_16_9) }
    var resolution by remember { mutableStateOf(ExportResolution.RES_1080P) }
    var fps by remember { mutableStateOf(FrameRateOption.FPS_30) }
    var selectedVisualUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedAudioUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isCreating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val visualPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        if (uris.isNotEmpty()) selectedVisualUris = selectedVisualUris + uris
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) selectedAudioUris = selectedAudioUris + uris
    }

    Scaffold(
        containerColor = StudioBackground,
        topBar = {
            TopAppBar(
                title = { Text("New Studio Project") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Project Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("new_project_title_input")
            )

            Text("Canvas Aspect Ratio", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AspectRatioPreset.entries.forEach { preset ->
                    val selected = preset == aspectRatio
                    Surface(
                        color = if (selected) CyanContainer else StudioSurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, if (selected) CyanPrimary else StudioBorder, RoundedCornerShape(10.dp))
                            .clickable { aspectRatio = preset }
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${preset.widthRatio}:${preset.heightRatio}",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (selected) CyanPrimary else TextPrimary
                            )
                            Text(
                                text = preset.label.substringAfter(" "),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            Text("Default Export Resolution & Frame Rate", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ExportResolution.RES_720P,
                    ExportResolution.RES_1080P,
                    ExportResolution.RES_2K,
                    ExportResolution.RES_4K
                ).forEach { res ->
                    val selected = res == resolution
                    Surface(
                        color = if (selected) CyanContainer else StudioSurface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, if (selected) CyanPrimary else StudioBorder, RoundedCornerShape(8.dp))
                            .clickable { resolution = res }
                    ) {
                        Text(
                            text = res.label.substringBefore(" "),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) CyanPrimary else TextSecondary,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FrameRateOption.entries.forEach { opt ->
                    val selected = opt == fps
                    Surface(
                        color = if (selected) CyanContainer else StudioSurface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, if (selected) CyanPrimary else StudioBorder, RoundedCornerShape(8.dp))
                            .clickable { fps = opt }
                    ) {
                        Text(
                            text = opt.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) CyanPrimary else TextSecondary,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp)
                        )
                    }
                }
            }

            // Optional Immediate Media Import
            Surface(
                color = StudioSurface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Import Initial Media (Optional)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        text = "You can also import images, videos, or 15-min narration audio inside the editor anytime.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                visualPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Photos/Videos (${selectedVisualUris.size})")
                        }
                        OutlinedButton(
                            onClick = { audioPicker.launch(arrayOf("audio/*")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.AudioFile, contentDescription = null, tint = EmeraldAudio)
                            Spacer(Modifier.width(6.dp))
                            Text("Audio (${selectedAudioUris.size})")
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (!isCreating) {
                        isCreating = true
                        scope.launch {
                            onCreateProject(title, aspectRatio, resolution, fps, selectedVisualUris, selectedAudioUris)
                        }
                    }
                },
                enabled = !isCreating,
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = OnCyan),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("create_project_submit_btn")
            ) {
                if (isCreating) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), color = OnCyan, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Preparing Timeline...")
                } else {
                    Icon(Icons.Rounded.MovieCreation, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open in Studio Editor")
                }
            }
        }
    }
}

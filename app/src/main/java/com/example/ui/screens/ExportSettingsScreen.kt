package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.domain.model.BitratePreset
import com.example.domain.model.ExportProgressState
import com.example.domain.model.ExportResolution
import com.example.domain.model.ExportSettings
import com.example.domain.model.FrameRateOption
import com.example.domain.model.VideoProject
import com.example.engine.TimelineEngine
import com.example.media.DeviceEncoderCapabilities
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
fun ExportSettingsScreen(
    project: VideoProject?,
    capabilities: DeviceEncoderCapabilities,
    exportState: ExportProgressState,
    onBack: () -> Unit,
    onStartExport: (ExportSettings) -> Unit,
    onCancelExport: () -> Unit,
    onResetExport: () -> Unit
) {
    val context = LocalContext.current
    var resolution by remember(project?.id) {
        mutableStateOf(project?.targetResolution ?: ExportResolution.RES_1080P)
    }
    var fps by remember(project?.id) {
        mutableStateOf(project?.targetFps ?: FrameRateOption.FPS_30)
    }
    var bitrate by remember(project?.id) { mutableStateOf(BitratePreset.HIGH) }
    var fileName by remember(project?.id) {
        mutableStateOf((project?.title ?: "VoxCut_Story").replace(" ", "_"))
    }

    val durationSec = ((project?.timeline?.totalDurationMs ?: 10_000L) / 1000.0)
    val estSizeMb = ((bitrate.bps / 8.0) * durationSec) / (1024.0 * 1024.0)
    val availMb = capabilities.availableStorageBytes / (1024L * 1024L)

    Scaffold(
        containerColor = StudioBackground,
        topBar = {
            TopAppBar(
                title = { Text("Export MP4 (Media3 Engine)") },
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
                .widthIn(max = 660.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = fileName,
                onValueChange = { fileName = it },
                label = { Text("Output File Name (.mp4)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Resolution Selector with Hardware Capability Check
            Text("Resolution", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ExportResolution.entries.forEach { res ->
                    val supported = when (res) {
                        ExportResolution.RES_4K -> capabilities.supports4k
                        ExportResolution.RES_2K -> capabilities.supports2k
                        else -> true
                    }
                    val selected = res == resolution
                    Surface(
                        color = if (selected) CyanContainer else StudioSurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, if (selected) CyanPrimary else StudioBorder, RoundedCornerShape(10.dp))
                            .clickable(enabled = supported) { resolution = res }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = res.label.substringBefore(" "),
                                style = MaterialTheme.typography.labelLarge,
                                color = when {
                                    !supported -> TextMuted
                                    selected -> CyanPrimary
                                    else -> TextPrimary
                                }
                            )
                            Text(
                                text = if (supported) "MP4" else "N/A",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (supported) TextSecondary else RoseError
                            )
                        }
                    }
                }
            }

            // Frame Rate Selector
            Text("Frame Rate", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FrameRateOption.entries.forEach { opt ->
                    val supported = if (opt == FrameRateOption.FPS_60) capabilities.supports60Fps else true
                    val selected = opt == fps
                    Surface(
                        color = if (selected) CyanContainer else StudioSurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, if (selected) CyanPrimary else StudioBorder, RoundedCornerShape(10.dp))
                            .clickable(enabled = supported) { fps = opt }
                    ) {
                        Text(
                            text = opt.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) CyanPrimary else if (supported) TextPrimary else TextMuted,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // Bitrate Selector
            Text("Video Bitrate", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BitratePreset.entries.forEach { br ->
                    val selected = br == bitrate
                    Surface(
                        color = if (selected) CyanContainer else StudioSurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, if (selected) CyanPrimary else StudioBorder, RoundedCornerShape(10.dp))
                            .clickable { bitrate = br }
                    ) {
                        Text(
                            text = br.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) CyanPrimary else TextPrimary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // Summary Info Card
            Surface(
                color = StudioSurface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Timeline Duration: ${TimelineEngine.formatMs(project?.timeline?.totalDurationMs ?: 0L)} • Clips: ${project?.timeline?.clips?.size ?: 0}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Text(
                        "Est. File Size: ${String.format("%.1f", estSizeMb)} MB • Free Storage: $availMb MB",
                        style = MaterialTheme.typography.labelMedium,
                        color = AmberAccent
                    )
                    Text(
                        "Destination: Movies/VoxCutStudio/$fileName.mp4",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            // Export Progress / Result Panel
            when (exportState) {
                is ExportProgressState.Idle -> {
                    Button(
                        onClick = {
                            onStartExport(ExportSettings(resolution, fps, bitrate, fileName))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = OnCyan),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("export_start_btn")
                    ) {
                        Icon(Icons.Rounded.Movie, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start Real MP4 Export")
                    }
                }
                is ExportProgressState.Running -> {
                    Surface(
                        color = StudioSurfaceElevated,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CyanPrimary, RoundedCornerShape(14.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(exportState.stage, style = MaterialTheme.typography.titleMedium, color = CyanPrimary)
                                Text("${exportState.percent}%", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                            }
                            LinearProgressIndicator(
                                progress = { exportState.percent / 100f },
                                color = CyanPrimary,
                                trackColor = StudioBackground,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            OutlinedButton(onClick = onCancelExport, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.Cancel, contentDescription = null, tint = RoseError)
                                Spacer(Modifier.width(6.dp))
                                Text("Cancel Export", color = RoseError)
                            }
                        }
                    }
                }
                is ExportProgressState.Completed -> {
                    Surface(
                        color = EmeraldContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, EmeraldAudio, RoundedCornerShape(14.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = EmeraldAudio)
                                Spacer(Modifier.width(8.dp))
                                Text("MP4 Export Completed!", style = MaterialTheme.typography.titleMedium, color = EmeraldAudio)
                            }
                            Text(
                                "File: ${exportState.outputFilePath}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                            Text(
                                "Size: ${String.format("%.2f", exportState.fileSizeBytes / (1024f * 1024f))} MB • Duration: ${TimelineEngine.formatMs(exportState.durationMs)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val fileUri = runCatching {
                                            FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                File(exportState.outputFilePath)
                                            )
                                        }.getOrElse { Uri.parse(exportState.contentUriString) }
                                        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(fileUri, "video/mp4")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        runCatching { context.startActivity(viewIntent) }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldAudio, contentColor = StudioBackground),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Play MP4")
                                }
                                OutlinedButton(
                                    onClick = {
                                        val fileUri = runCatching {
                                            FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                File(exportState.outputFilePath)
                                            )
                                        }.getOrElse { Uri.parse(exportState.contentUriString) }
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "video/mp4"
                                            putExtra(Intent.EXTRA_STREAM, fileUri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        runCatching { context.startActivity(Intent.createChooser(shareIntent, "Share Exported Video")) }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Share, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Share")
                                }
                            }
                            TextButton(onClick = onResetExport, modifier = Modifier.align(Alignment.End)) {
                                Text("Export Another Version")
                            }
                        }
                    }
                }
                is ExportProgressState.Failed -> {
                    Surface(
                        color = RoseContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, RoseError, RoundedCornerShape(14.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = RoseError)
                                Spacer(Modifier.width(8.dp))
                                Text("Export Failed", style = MaterialTheme.typography.titleMedium, color = RoseError)
                            }
                            Text(exportState.errorMessage, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Button(
                                onClick = onResetExport,
                                colors = ButtonDefaults.buttonColors(containerColor = RoseError, contentColor = StudioBackground)
                            ) {
                                Text("Try Again")
                            }
                        }
                    }
                }
            }
        }
    }
}

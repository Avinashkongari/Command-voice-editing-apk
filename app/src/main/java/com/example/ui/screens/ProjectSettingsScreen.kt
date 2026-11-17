package com.example.ui.screens

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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.EditHistoryRecord
import com.example.domain.model.ExportResolution
import com.example.domain.model.FrameRateOption
import com.example.domain.model.VideoProject
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.OnCyan
import com.example.ui.theme.RoseError
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectSettingsScreen(
    project: VideoProject?,
    history: List<EditHistoryRecord>,
    onBack: () -> Unit,
    onSaveSettings: (
        title: String,
        aspectRatio: AspectRatioPreset,
        resolution: ExportResolution,
        fps: FrameRateOption,
        autosave: Boolean,
        defaultImageDurMs: Long,
        defaultTransitionDurMs: Long
    ) -> Unit,
    onDuplicateProject: () -> Unit,
    onDeleteProject: () -> Unit
) {
    var title by remember(project?.id) { mutableStateOf(project?.title ?: "Untitled") }
    var aspect by remember(project?.id) { mutableStateOf(project?.aspectRatio ?: AspectRatioPreset.RATIO_16_9) }
    var res by remember(project?.id) { mutableStateOf(project?.targetResolution ?: ExportResolution.RES_1080P) }
    var fps by remember(project?.id) { mutableStateOf(project?.targetFps ?: FrameRateOption.FPS_30) }
    var autosave by remember(project?.id) { mutableStateOf(project?.autosaveEnabled ?: true) }
    var defaultImgMs by remember(project?.id) { mutableLongStateOf(project?.timeline?.defaultImageDurationMs ?: 5000L) }
    var defaultTransMs by remember(project?.id) { mutableLongStateOf(project?.timeline?.defaultTransitionDurationMs ?: 500L) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = StudioBackground,
        topBar = {
            TopAppBar(
                title = { Text("Project Settings") },
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
                value = title,
                onValueChange = { title = it },
                label = { Text("Project Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Aspect Ratio", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AspectRatioPreset.entries.forEach { preset ->
                    val selected = preset == aspect
                    Surface(
                        color = if (selected) CyanContainer else StudioSurface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, if (selected) CyanPrimary else StudioBorder, RoundedCornerShape(8.dp))
                            .clickable { aspect = preset }
                    ) {
                        Text(
                            text = "${preset.widthRatio}:${preset.heightRatio}",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) CyanPrimary else TextPrimary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Autosave Timeline", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text("Automatically persist every voice & manual edit", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                Switch(checked = autosave, onCheckedChange = { autosave = it })
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = (defaultImgMs / 1000L).toString(),
                    onValueChange = { v -> defaultImgMs = (v.toLongOrNull() ?: 5L).coerceIn(1L, 60L) * 1000L },
                    label = { Text("Default Image (sec)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = defaultTransMs.toString(),
                    onValueChange = { v -> defaultTransMs = (v.toLongOrNull() ?: 500L).coerceIn(200L, 3000L) },
                    label = { Text("Transition (ms)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = {
                    onSaveSettings(title, aspect, res, fps, autosave, defaultImgMs, defaultTransMs)
                    onBack()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = OnCyan),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save Project Settings")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDuplicateProject, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Duplicate")
                }
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = RoseError)
                    Spacer(Modifier.width(6.dp))
                    Text("Delete", color = RoseError)
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.History, contentDescription = null, tint = CyanPrimary)
                Spacer(Modifier.width(8.dp))
                Text("Editing History (${history.size})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }

            if (history.isEmpty()) {
                Text("No editing history recorded yet.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            } else {
                history.take(20).forEach { entry ->
                    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(entry.timestampMs))
                    Surface(
                        color = StudioSurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(entry.description, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                            Text(timeStr, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete Project?") },
            text = { Text("This deletes the project timeline from VoxCut Studio. Original media files on your device remain untouched.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDeleteProject()
                }) { Text("Delete", color = RoseError) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

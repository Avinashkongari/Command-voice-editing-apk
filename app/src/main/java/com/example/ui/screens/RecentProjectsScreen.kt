package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.VideoProject
import com.example.engine.TimelineEngine
import com.example.ui.theme.AmberAccent
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
fun RecentProjectsScreen(
    projects: List<VideoProject>,
    onBack: () -> Unit,
    onNewProject: () -> Unit,
    onOpenProject: (String) -> Unit,
    onProjectSettings: (String) -> Unit,
    onRenameProject: (String, String) -> Unit,
    onDuplicateProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var projectToRename by remember { mutableStateOf<VideoProject?>(null) }
    var renameText by remember { mutableStateOf("") }
    var projectToDelete by remember { mutableStateOf<VideoProject?>(null) }

    val filtered = remember(projects, query) {
        if (query.isBlank()) projects
        else projects.filter { it.title.contains(query, ignoreCase = true) }
    }

    Scaffold(
        containerColor = StudioBackground,
        topBar = {
            TopAppBar(
                title = { Text("Recent Projects (${projects.size})") },
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewProject,
                containerColor = CyanPrimary,
                contentColor = OnCyan,
                modifier = Modifier.testTag("recent_new_project_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "New Project")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search projects...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            if (filtered.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (projects.isEmpty()) "No projects yet." else "No projects match \"$query\".",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextMuted
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 84.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { proj ->
                        val dateStr = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
                            .format(Date(proj.updatedAtMs))
                        Surface(
                            color = StudioSurface,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
                                .clickable { onOpenProject(proj.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.MovieCreation, contentDescription = null, tint = CyanPrimary)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(proj.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Surface(color = CyanContainer, shape = RoundedCornerShape(4.dp)) {
                                            Text(
                                                "${proj.aspectRatio.widthRatio}:${proj.aspectRatio.heightRatio}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = CyanPrimary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            "${proj.timeline.clips.size} clips • ${TimelineEngine.formatMs(proj.timeline.totalDurationMs)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = AmberAccent
                                        )
                                    }
                                    Text("Updated $dateStr", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                                }
                                IconButton(onClick = {
                                    projectToRename = proj
                                    renameText = proj.title
                                }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Rename", tint = TextSecondary)
                                }
                                IconButton(onClick = { onDuplicateProject(proj.id) }) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Duplicate", tint = TextSecondary)
                                }
                                IconButton(onClick = { onProjectSettings(proj.id) }) {
                                    Icon(Icons.Rounded.Settings, contentDescription = "Project Settings", tint = TextSecondary)
                                }
                                IconButton(onClick = { projectToDelete = proj }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = RoseError)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (projectToRename != null) {
        AlertDialog(
            onDismissRequest = { projectToRename = null },
            title = { Text("Rename Project") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Project Title") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    projectToRename?.let { onRenameProject(it.id, renameText) }
                    projectToRename = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { projectToRename = null }) { Text("Cancel") }
            }
        )
    }

    if (projectToDelete != null) {
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Delete Project?") },
            text = { Text("Delete \"${projectToDelete?.title}\"? Your original device media files are never deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    projectToDelete?.let { onDeleteProject(it.id) }
                    projectToDelete = null
                }) { Text("Delete", color = RoseError) }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

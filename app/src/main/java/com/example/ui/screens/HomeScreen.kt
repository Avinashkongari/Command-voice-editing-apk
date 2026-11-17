package com.example.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AiProviderProfile
import com.example.domain.model.VideoProject
import com.example.engine.TimelineEngine
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
import com.example.ui.theme.StudioVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletTrack
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    projects: List<VideoProject>,
    activeAiProfile: AiProviderProfile?,
    onNewProject: () -> Unit,
    onRecentProjects: () -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenAiSettings: () -> Unit
) {
    val isAiReady = activeAiProfile?.isConnectionTested == true && activeAiProfile.hasStoredApiKey

    Scaffold(
        containerColor = StudioBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Studio Top Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "VOXCUT STUDIO",
                                style = MaterialTheme.typography.headlineMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Voice-Directed Multi-Track Editor",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        IconButton(
                            onClick = onOpenAiSettings,
                            modifier = Modifier
                                .size(48.dp)
                                .background(StudioSurfaceElevated, CircleShape)
                                .border(1.dp, StudioBorder, CircleShape)
                                .testTag("home_ai_settings_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "AI API Settings",
                                tint = CyanPrimary
                            )
                        }
                    }
                }

                // Hero Studio Banner
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, StudioBorder, RoundedCornerShape(20.dp))
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_studio_hero),
                            contentDescription = "VoxCut Studio Hero",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            StudioVoid.copy(alpha = 0.25f),
                                            StudioVoid.copy(alpha = 0.92f)
                                        )
                                    )
                                )
                                .padding(18.dp)
                        ) {
                            // AI Status Pill
                            Surface(
                                color = if (isAiReady) EmeraldContainer else RoseContainer,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { onOpenAiSettings() }
                                    .testTag("home_ai_status_pill")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(if (isAiReady) EmeraldAudio else RoseError, CircleShape)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (isAiReady) {
                                            "AI Ready • ${activeAiProfile?.providerName} (${activeAiProfile?.selectedModelId})"
                                        } else {
                                            "AI Not Configured — Tap to Setup & Test"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isAiReady) EmeraldAudio else RoseError,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                                Text(
                                    text = "Edit Stories by Voice",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = TextPrimary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Speak naturally in Hindi, English, or Hinglish. Multi-track timeline + Media3 MP4 export.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                // Primary Actions Row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onNewProject,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanPrimary,
                                contentColor = OnCyan
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("home_new_project_btn")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("New Project", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onRecentProjects,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("home_recent_projects_btn")
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = TextPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("All Projects (${projects.size})", color = TextPrimary)
                        }
                    }
                }

                // Quick Tools & AI Setup Strip
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickStudioTile(
                            icon = Icons.Rounded.Tune,
                            title = "AI Providers",
                            subtitle = "Gemini / OpenAI / Custom",
                            accent = CyanPrimary,
                            onClick = onOpenAiSettings,
                            modifier = Modifier.weight(1f).testTag("home_tile_ai_providers")
                        )
                        QuickStudioTile(
                            icon = Icons.Rounded.VerifiedUser,
                            title = "Keystore Vault",
                            subtitle = "AES-256 Encrypted",
                            accent = EmeraldAudio,
                            onClick = onOpenAiSettings,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Recent Projects Section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Studio Projects",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        if (projects.isNotEmpty()) {
                            Text(
                                text = "View All",
                                style = MaterialTheme.typography.labelLarge,
                                color = CyanPrimary,
                                modifier = Modifier
                                    .clickable { onRecentProjects() }
                                    .padding(8.dp)
                            )
                        }
                    }
                }

                if (projects.isEmpty()) {
                    item {
                        Surface(
                            color = StudioSurface,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_empty_studio),
                                    contentDescription = "Empty Studio",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(88.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Start Your First Edit",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Create a project, import narration audio or video clips, and direct edits with your voice.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(projects.take(6), key = { it.id }) { proj ->
                                ProjectSummaryCard(
                                    project = proj,
                                    onClick = { onOpenProject(proj.id) }
                                )
                            }
                        }
                    }
                }

                // Story Narration Workflow Card
                item {
                    Surface(
                        color = StudioSurface,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Voice Story Editing Workflow",
                                style = MaterialTheme.typography.titleMedium,
                                color = AmberAccent
                            )
                            WorkflowStepRow(Icons.Rounded.Settings, "1. Connect & Test your AI provider in AI API Settings", CyanPrimary)
                            WorkflowStepRow(Icons.Rounded.GraphicEq, "2. Import 15-min story audio, photos & video clips", EmeraldAudio)
                            WorkflowStepRow(Icons.Rounded.Mic, "3. Speak naturally: \"Is image ko 1:20 se 1:38 tak lagao\"", AmberAccent)
                            WorkflowStepRow(Icons.Rounded.AutoAwesome, "4. Watch live timeline edits & export MP4 up to 4K", VioletTrack)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickStudioTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = StudioSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(accent.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ProjectSummaryCard(project: VideoProject, onClick: () -> Unit) {
    val dateStr = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(project.updatedAtMs))
    Surface(
        color = StudioSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .width(210.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("home_project_card_${project.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.MovieCreation, contentDescription = null, tint = CyanPrimary)
                Surface(color = CyanContainer, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        text = "${project.aspectRatio.widthRatio}:${project.aspectRatio.heightRatio}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = project.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${project.timeline.clips.size} clips • ${TimelineEngine.formatMs(project.timeline.totalDurationMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = AmberAccent
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Edited $dateStr",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun WorkflowStepRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

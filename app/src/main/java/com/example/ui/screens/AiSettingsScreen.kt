package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.domain.model.AiProviderProfile
import com.example.domain.model.AiProviderType
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
import com.example.ui.viewmodel.AiSettingsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    uiState: AiSettingsUiState,
    savedProfiles: List<AiProviderProfile>,
    onBack: () -> Unit,
    onSelectSavedProfile: (AiProviderProfile) -> Unit,
    onActivateProfile: (AiProviderProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onNewProfile: (AiProviderType) -> Unit,
    onProviderTypeChange: (AiProviderType) -> Unit,
    onProviderNameChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onCustomChatPathChange: (String) -> Unit,
    onCustomAuthHeaderChange: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onLoadModels: () -> Unit,
    onTestConnection: () -> Unit,
    onSaveConfiguration: () -> Unit,
    onDeleteCredential: () -> Unit
) {
    var showApiKey by remember { mutableStateOf(false) }
    var modelDropdownOpen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = StudioBackground,
        topBar = {
            TopAppBar(
                title = { Text("AI Provider & Model Settings") },
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
                .widthIn(max = 680.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Security Assurance Banner
            Surface(
                color = EmeraldContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, EmeraldAudio.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = EmeraldAudio)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Android Keystore AES-256-GCM Encrypted", style = MaterialTheme.typography.titleMedium, color = EmeraldAudio)
                        Text(
                            "API keys are encrypted in hardware-backed storage, never logged, and only command metadata is sent.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Saved Provider Profiles Strip
            if (savedProfiles.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Saved Providers", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    OutlinedButton(onClick = { onNewProfile(AiProviderType.GEMINI) }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New Profile")
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    savedProfiles.forEach { prof ->
                        val isEditing = prof.id == uiState.editingProfileId
                        Surface(
                            color = if (prof.isActive) CyanContainer else StudioSurface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    1.dp,
                                    if (isEditing) CyanPrimary else StudioBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    onSelectSavedProfile(prof)
                                    onActivateProfile(prof)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(if (prof.isConnectionTested) EmeraldAudio else AmberAccent, CircleShape)
                                )
                                Spacer(Modifier.width(6.dp))
                                Column {
                                    Text(prof.providerName, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                                    Text(
                                        prof.selectedModelId.ifBlank { "No model selected" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                IconButton(
                                    onClick = { onDeleteProfile(prof.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete profile", tint = RoseError, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Provider Adapter Type Selector
            Text("Provider Adapter Type", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AiProviderType.entries.forEach { type ->
                    val selected = uiState.providerType == type
                    Surface(
                        color = if (selected) CyanContainer else StudioSurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, if (selected) CyanPrimary else StudioBorder, RoundedCornerShape(10.dp))
                            .clickable { onProviderTypeChange(type) }
                            .testTag("ai_provider_type_${type.name.lowercase()}")
                    ) {
                        Text(
                            text = when (type) {
                                AiProviderType.GEMINI -> "Gemini API"
                                AiProviderType.OPENAI_COMPATIBLE -> "OpenAI Compat"
                                AiProviderType.CUSTOM -> "Custom REST"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) CyanPrimary else TextSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = uiState.providerName,
                onValueChange = onProviderNameChange,
                label = { Text("Provider Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_provider_name_input")
            )

            OutlinedTextField(
                value = uiState.baseUrl,
                onValueChange = onBaseUrlChange,
                label = { Text("API Base URL") },
                placeholder = { Text(uiState.providerType.defaultBaseUrl) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_base_url_input")
            )

            if (uiState.providerType == AiProviderType.CUSTOM) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.customChatPath,
                        onValueChange = onCustomChatPathChange,
                        label = { Text("Completion Path") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.customAuthHeader,
                        onValueChange = onCustomAuthHeaderChange,
                        label = { Text("Auth Header") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // API Key Input + Masked Existing Key Status
            OutlinedTextField(
                value = uiState.apiKeyInput,
                onValueChange = onApiKeyChange,
                label = {
                    Text(
                        if (uiState.hasStoredKey) {
                            "API Key (Saved: ${uiState.maskedSavedKey} — leave blank to keep)"
                        } else {
                            "API Key"
                        }
                    )
                },
                leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            if (showApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "Toggle API key visibility"
                        )
                    }
                },
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_api_key_input")
            )

            if (uiState.hasStoredKey) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDeleteCredential) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, tint = RoseError, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete Stored API Key", color = RoseError)
                    }
                }
            }

            // Model Discovery Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onLoadModels,
                    enabled = !uiState.isLoadingModels,
                    colors = ButtonDefaults.buttonColors(containerColor = StudioSurfaceElevated, contentColor = CyanPrimary),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("ai_load_models_btn")
                ) {
                    if (uiState.isLoadingModels) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = CyanPrimary)
                    } else {
                        Icon(Icons.Rounded.CloudSync, contentDescription = null)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("Load Models")
                }

                OutlinedButton(
                    onClick = onLoadModels,
                    enabled = !uiState.isLoadingModels,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("ai_refresh_models_btn")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Models")
                    Spacer(Modifier.width(4.dp))
                    Text("Refresh")
                }
            }

            // Discovered Models Dropdown
            if (uiState.availableModels.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        color = StudioSurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                            .clickable { modelDropdownOpen = true }
                            .testTag("ai_model_dropdown")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val selectedInfo = uiState.availableModels.firstOrNull { it.id == uiState.selectedModelId }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedInfo?.displayName ?: uiState.selectedModelId.ifBlank { "Select a discovered model" },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = CyanPrimary
                                )
                                if (!selectedInfo?.capabilities.isNullOrBlank()) {
                                    Text(
                                        text = selectedInfo?.capabilities.orEmpty(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                            Icon(Icons.Rounded.ExpandMore, contentDescription = "Expand models", tint = TextSecondary)
                        }
                    }

                    DropdownMenu(
                        expanded = modelDropdownOpen,
                        onDismissRequest = { modelDropdownOpen = false },
                        modifier = Modifier.background(StudioSurfaceElevated)
                    ) {
                        uiState.availableModels.forEach { model ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(model.id, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                        if (model.capabilities.isNotBlank()) {
                                            Text(model.capabilities, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        }
                                    }
                                },
                                onClick = {
                                    onSelectModel(model.id)
                                    modelDropdownOpen = false
                                }
                            )
                        }
                    }
                }
            }

            // Manual Model ID Entry (always available, especially if discovery isn't supported)
            OutlinedTextField(
                value = uiState.selectedModelId,
                onValueChange = onSelectModel,
                label = { Text("Model ID (Select above or enter manually)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_model_id_input")
            )

            // Connection Status Banner
            Surface(
                color = when {
                    uiState.isError -> RoseContainer
                    uiState.isConnectionTested -> EmeraldContainer
                    else -> StudioSurface
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        when {
                            uiState.isError -> RoseError
                            uiState.isConnectionTested -> EmeraldAudio
                            else -> StudioBorder
                        },
                        RoundedCornerShape(10.dp)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = when {
                            uiState.isError -> RoseError
                            uiState.isConnectionTested -> EmeraldAudio
                            else -> TextMuted
                        }
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = uiState.statusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            uiState.isError -> RoseError
                            uiState.isConnectionTested -> EmeraldAudio
                            else -> TextSecondary
                        }
                    )
                }
            }

            // Test Connection & Save Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onTestConnection,
                    enabled = !uiState.isTestingConnection,
                    colors = ButtonDefaults.buttonColors(containerColor = AmberAccent, contentColor = StudioBackground),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("ai_test_connection_btn")
                ) {
                    if (uiState.isTestingConnection) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = StudioBackground)
                        Spacer(Modifier.width(6.dp))
                        Text("Testing...")
                    } else {
                        Text("Test Connection")
                    }
                }

                Button(
                    onClick = onSaveConfiguration,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = OnCyan),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("ai_save_config_btn")
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Save Config")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

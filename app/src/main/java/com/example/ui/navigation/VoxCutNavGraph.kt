package com.example.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.AppContainer
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.AiSettingsScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.ExportSettingsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MediaLibraryScreen
import com.example.ui.screens.NewProjectScreen
import com.example.ui.screens.ProjectSettingsScreen
import com.example.ui.screens.RecentProjectsScreen
import com.example.ui.viewmodel.EditorViewModel
import com.example.ui.viewmodel.MainViewModel

object Routes {
    const val HOME = "home"
    const val NEW_PROJECT = "new_project"
    const val RECENT_PROJECTS = "recent_projects"
    const val AI_SETTINGS = "ai_settings"
    const val EDITOR = "editor/{projectId}"
    const val AI_ASSISTANT = "ai_assistant/{projectId}"
    const val MEDIA_LIBRARY = "media_library/{projectId}"
    const val EXPORT_SETTINGS = "export_settings/{projectId}"
    const val PROJECT_SETTINGS = "project_settings/{projectId}"

    fun editor(id: String) = "editor/$id"
    fun aiAssistant(id: String) = "ai_assistant/$id"
    fun mediaLibrary(id: String) = "media_library/$id"
    fun exportSettings(id: String) = "export_settings/$id"
    fun projectSettings(id: String) = "project_settings/$id"
}

@Composable
fun VoxCutNavGraph(container: AppContainer) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val mainViewModel: MainViewModel = viewModel(factory = MainViewModel.Factory(container))

    val projects by mainViewModel.projects.collectAsStateWithLifecycle()
    val activeAiProfile by mainViewModel.activeAiProfile.collectAsStateWithLifecycle()
    val aiProfiles by mainViewModel.aiProfiles.collectAsStateWithLifecycle()
    val aiSettingsUi by mainViewModel.aiSettingsUi.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                projects = projects,
                activeAiProfile = activeAiProfile,
                onNewProject = { navController.navigate(Routes.NEW_PROJECT) },
                onRecentProjects = { navController.navigate(Routes.RECENT_PROJECTS) },
                onOpenProject = { id -> navController.navigate(Routes.editor(id)) },
                onOpenAiSettings = { navController.navigate(Routes.AI_SETTINGS) }
            )
        }

        composable(Routes.NEW_PROJECT) {
            NewProjectScreen(
                onBack = { navController.popBackStack() },
                onCreateProject = { title, ratio, res, fps, visualUris, audioUris ->
                    val created = mainViewModel.createProject(title, ratio, res, fps, visualUris, audioUris)
                    navController.navigate(Routes.editor(created.id)) {
                        popUpTo(Routes.NEW_PROJECT) { inclusive = true }
                    }
                    created.id
                }
            )
        }

        composable(Routes.RECENT_PROJECTS) {
            RecentProjectsScreen(
                projects = projects,
                onBack = { navController.popBackStack() },
                onNewProject = { navController.navigate(Routes.NEW_PROJECT) },
                onOpenProject = { id -> navController.navigate(Routes.editor(id)) },
                onProjectSettings = { id -> navController.navigate(Routes.projectSettings(id)) },
                onRenameProject = { id, newTitle -> mainViewModel.renameProject(id, newTitle) },
                onDuplicateProject = { id -> mainViewModel.duplicateProject(id) },
                onDeleteProject = { id -> mainViewModel.deleteProject(id) }
            )
        }

        composable(Routes.AI_SETTINGS) {
            AiSettingsScreen(
                uiState = aiSettingsUi,
                savedProfiles = aiProfiles,
                onBack = { navController.popBackStack() },
                onSelectSavedProfile = { mainViewModel.loadProfileIntoForm(it) },
                onActivateProfile = { mainViewModel.activateProfile(it) },
                onDeleteProfile = { mainViewModel.deleteProfile(it) },
                onNewProfile = { mainViewModel.startNewProviderForm(it) },
                onProviderTypeChange = { mainViewModel.updateProviderType(it) },
                onProviderNameChange = { mainViewModel.updateProviderName(it) },
                onBaseUrlChange = { mainViewModel.updateBaseUrl(it) },
                onApiKeyChange = { mainViewModel.updateApiKeyInput(it) },
                onCustomChatPathChange = { mainViewModel.updateCustomChatPath(it) },
                onCustomAuthHeaderChange = { mainViewModel.updateCustomAuthHeader(it) },
                onSelectModel = { mainViewModel.selectModel(it) },
                onLoadModels = { mainViewModel.fetchModels() },
                onTestConnection = { mainViewModel.testConnection() },
                onSaveConfiguration = { mainViewModel.saveConfiguration() },
                onDeleteCredential = { mainViewModel.deleteStoredCredential() }
            )
        }

        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { entry ->
            val projectId = entry.arguments?.getString("projectId").orEmpty()
            val editorViewModel: EditorViewModel = viewModel(
                viewModelStoreOwner = entry,
                factory = EditorViewModel.Factory(projectId, container, context.applicationContext)
            )
            EditorScreen(
                viewModel = editorViewModel,
                onBack = { navController.popBackStack() },
                onOpenMediaLibrary = { navController.navigate(Routes.mediaLibrary(projectId)) },
                onOpenAiAssistantFull = { navController.navigate(Routes.aiAssistant(projectId)) },
                onOpenAiSettings = { navController.navigate(Routes.AI_SETTINGS) },
                onOpenExport = { navController.navigate(Routes.exportSettings(projectId)) },
                onOpenProjectSettings = { navController.navigate(Routes.projectSettings(projectId)) }
            )
        }

        composable(
            route = Routes.MEDIA_LIBRARY,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { entry ->
            val projectId = entry.arguments?.getString("projectId").orEmpty()
            val parentEntry = remember(entry) {
                runCatching { navController.getBackStackEntry(Routes.editor(projectId)) }.getOrNull() ?: entry
            }
            val editorViewModel: EditorViewModel = viewModel(
                viewModelStoreOwner = parentEntry,
                factory = EditorViewModel.Factory(projectId, container, context.applicationContext)
            )
            val assets by editorViewModel.assets.collectAsStateWithLifecycle()
            val uiState by editorViewModel.uiState.collectAsStateWithLifecycle()
            val recorderState by editorViewModel.voiceRecorderState.collectAsStateWithLifecycle()

            MediaLibraryScreen(
                assets = assets,
                selectedAssetId = uiState.selectedAssetId,
                isImporting = uiState.isImportingMedia,
                importError = uiState.importError,
                voiceRecorderState = recorderState,
                onBack = { navController.popBackStack() },
                onImportVisualUris = { editorViewModel.importMediaUris(it) },
                onImportAudioUris = { editorViewModel.importMediaUris(it, forcedType = com.example.domain.model.MediaAssetType.AUDIO) },
                onToggleVoiceRecord = { editorViewModel.toggleVoiceoverRecording() },
                onSelectAssetForAi = { editorViewModel.selectAsset(it) },
                onAddAssetToTimeline = { editorViewModel.addAssetToTimelineAtPlayhead(it) },
                onUpdateAssetTags = { asset, tags, name -> editorViewModel.updateAssetTags(asset, tags, name) },
                onDeleteAsset = { editorViewModel.deleteAsset(it) }
            )
        }

        composable(
            route = Routes.AI_ASSISTANT,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { entry ->
            val projectId = entry.arguments?.getString("projectId").orEmpty()
            val parentEntry = remember(entry) {
                runCatching { navController.getBackStackEntry(Routes.editor(projectId)) }.getOrNull() ?: entry
            }
            val editorViewModel: EditorViewModel = viewModel(
                viewModelStoreOwner = parentEntry,
                factory = EditorViewModel.Factory(projectId, container, context.applicationContext)
            )
            val uiState by editorViewModel.uiState.collectAsStateWithLifecycle()
            val messages by editorViewModel.chatMessages.collectAsStateWithLifecycle()
            val voiceState by editorViewModel.voiceRecognitionState.collectAsStateWithLifecycle()
            val activeProfile by editorViewModel.activeAiProfile.collectAsStateWithLifecycle()

            AiAssistantScreen(
                messages = messages,
                activeProfile = activeProfile,
                isProcessing = uiState.isAiProcessing,
                voiceState = voiceState,
                voiceLanguageMode = uiState.voiceLanguageMode,
                onBack = { navController.popBackStack() },
                onLanguageModeChange = { editorViewModel.setVoiceLanguageMode(it) },
                onStartListening = { editorViewModel.voiceRecognizer.startListening(uiState.voiceLanguageMode) },
                onStopListening = { editorViewModel.voiceRecognizer.stopListening() },
                onSendTextCommand = { editorViewModel.submitAiCommand(it, isVoice = false) },
                onImportVisualUris = { editorViewModel.importMediaUris(it) },
                onImportAudioUris = { editorViewModel.importMediaUris(it, forcedType = com.example.domain.model.MediaAssetType.AUDIO) },
                onOpenAiSettings = { navController.navigate(Routes.AI_SETTINGS) }
            )
        }

        composable(
            route = Routes.EXPORT_SETTINGS,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { entry ->
            val projectId = entry.arguments?.getString("projectId").orEmpty()
            val parentEntry = remember(entry) {
                runCatching { navController.getBackStackEntry(Routes.editor(projectId)) }.getOrNull() ?: entry
            }
            val editorViewModel: EditorViewModel = viewModel(
                viewModelStoreOwner = parentEntry,
                factory = EditorViewModel.Factory(projectId, container, context.applicationContext)
            )
            val uiState by editorViewModel.uiState.collectAsStateWithLifecycle()
            val exportState by editorViewModel.exportState.collectAsStateWithLifecycle()
            val capabilities = remember { editorViewModel.queryEncoderCapabilities() }

            ExportSettingsScreen(
                project = uiState.project,
                capabilities = capabilities,
                exportState = exportState,
                onBack = { navController.popBackStack() },
                onStartExport = { editorViewModel.startExport(it) },
                onCancelExport = { editorViewModel.cancelExport() },
                onResetExport = { editorViewModel.resetExportState() }
            )
        }

        composable(
            route = Routes.PROJECT_SETTINGS,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { entry ->
            val projectId = entry.arguments?.getString("projectId").orEmpty()
            val parentEntry = remember(entry) {
                runCatching { navController.getBackStackEntry(Routes.editor(projectId)) }.getOrNull() ?: entry
            }
            val editorViewModel: EditorViewModel = viewModel(
                viewModelStoreOwner = parentEntry,
                factory = EditorViewModel.Factory(projectId, container, context.applicationContext)
            )
            val uiState by editorViewModel.uiState.collectAsStateWithLifecycle()
            val history by editorViewModel.editHistory.collectAsStateWithLifecycle()

            ProjectSettingsScreen(
                project = uiState.project,
                history = history,
                onBack = { navController.popBackStack() },
                onSaveSettings = { title, ratio, res, fps, auto, imgMs, trMs ->
                    editorViewModel.updateProjectSettings(title, ratio, res, fps, auto, imgMs, trMs)
                },
                onDuplicateProject = {
                    mainViewModel.duplicateProject(projectId)
                    navController.popBackStack()
                },
                onDeleteProject = {
                    mainViewModel.deleteProject(projectId)
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
            )
        }
    }
}

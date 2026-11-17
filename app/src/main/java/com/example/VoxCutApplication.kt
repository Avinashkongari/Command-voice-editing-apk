package com.example

import android.app.Application
import com.example.ai.adapter.AiProviderRouter
import com.example.data.local.VoxCutDatabase
import com.example.data.repository.AiConfigRepository
import com.example.data.repository.ProjectRepository
import com.example.media.Media3ExportEngine
import com.example.media.MediaMetadataHelper
import com.example.media.VoiceRecorderManager
import com.example.media.WaveformExtractor
import com.example.security.KeystoreCredentialManager

class AppContainer(application: Application) {
    val database = VoxCutDatabase.getInstance(application)
    val credentialManager = KeystoreCredentialManager(application)
    val projectRepository = ProjectRepository(
        projectDao = database.projectDao(),
        mediaAssetDao = database.mediaAssetDao(),
        editHistoryDao = database.editHistoryDao()
    )
    val aiConfigRepository = AiConfigRepository(
        aiProviderDao = database.aiProviderDao(),
        credentialManager = credentialManager
    )
    val aiProviderRouter = AiProviderRouter(application)
    val metadataHelper = MediaMetadataHelper(application)
    val waveformExtractor = WaveformExtractor(application)
    val voiceRecorderManager = VoiceRecorderManager(application)
    val exportEngine = Media3ExportEngine(application, metadataHelper)
}

class VoxCutApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

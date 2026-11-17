package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val aspectRatioName: String,
    val targetResolutionName: String,
    val targetFpsName: String,
    val autosaveEnabled: Boolean,
    val timelineJson: String,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

@Entity(tableName = "media_assets")
data class MediaAssetEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val uriString: String,
    val displayName: String,
    val typeName: String,
    val mimeType: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val tags: String,
    val waveformPeaksJson: String,
    val thumbnailPath: String?,
    val importedAtMs: Long
)

@Entity(tableName = "edit_history")
data class EditHistoryEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val description: String,
    val timelineSnapshotJson: String,
    val timestampMs: Long
)

@Entity(tableName = "ai_provider_profiles")
data class AiProviderProfileEntity(
    @PrimaryKey val id: String,
    val providerName: String,
    val providerTypeName: String,
    val baseUrl: String,
    val selectedModelId: String,
    val customChatPath: String,
    val customAuthHeader: String,
    val availableModelsJson: String,
    val isConnectionTested: Boolean,
    val lastTestMessage: String,
    val lastTestedAtMs: Long,
    val isActive: Boolean
)

package com.example.data.repository

import com.example.data.local.EditHistoryDao
import com.example.data.local.EditHistoryEntity
import com.example.data.local.JsonMappers
import com.example.data.local.MediaAssetDao
import com.example.data.local.MediaAssetEntity
import com.example.data.local.ProjectDao
import com.example.data.local.ProjectEntity
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.EditHistoryRecord
import com.example.domain.model.ExportResolution
import com.example.domain.model.FrameRateOption
import com.example.domain.model.MediaAsset
import com.example.domain.model.MediaAssetType
import com.example.domain.model.TimelineDocument
import com.example.domain.model.VideoProject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class ProjectRepository(
    private val projectDao: ProjectDao,
    private val mediaAssetDao: MediaAssetDao,
    private val editHistoryDao: EditHistoryDao
) {
    fun observeAllProjects(): Flow<List<VideoProject>> =
        projectDao.observeAllProjects().map { list -> list.map { it.toDomain() } }

    fun observeProject(projectId: String): Flow<VideoProject?> =
        projectDao.observeProjectById(projectId).map { it?.toDomain() }

    suspend fun getProject(projectId: String): VideoProject? =
        projectDao.getProjectById(projectId)?.toDomain()

    suspend fun createProject(
        title: String,
        aspectRatio: AspectRatioPreset,
        resolution: ExportResolution,
        fps: FrameRateOption
    ): VideoProject {
        val now = System.currentTimeMillis()
        val project = VideoProject(
            id = UUID.randomUUID().toString(),
            title = title.trim().ifBlank { "Untitled Story" },
            aspectRatio = aspectRatio,
            targetResolution = resolution,
            targetFps = fps,
            autosaveEnabled = true,
            createdAtMs = now,
            updatedAtMs = now,
            timeline = TimelineDocument()
        )
        projectDao.upsertProject(project.toEntity())
        return project
    }

    suspend fun saveProject(project: VideoProject) {
        projectDao.upsertProject(project.copy(updatedAtMs = System.currentTimeMillis()).toEntity())
    }

    suspend fun renameProject(projectId: String, newTitle: String) {
        val existing = getProject(projectId) ?: return
        saveProject(existing.copy(title = newTitle.trim().ifBlank { existing.title }))
    }

    suspend fun duplicateProject(projectId: String): VideoProject? {
        val existing = getProject(projectId) ?: return null
        val assets = mediaAssetDao.getAssetsForProject(projectId)
        val newId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val duplicated = existing.copy(
            id = newId,
            title = "${existing.title} (Copy)",
            createdAtMs = now,
            updatedAtMs = now
        )
        projectDao.upsertProject(duplicated.toEntity())
        assets.forEach { asset ->
            mediaAssetDao.upsertAsset(
                asset.copy(
                    id = asset.id, // keep asset references intact or remap if needed; per-project PK needs unique id
                    projectId = newId
                ).let { entity ->
                    // Generate unique DB row id while preserving clip assetId mapping via remap below if needed
                    entity
                }
            )
        }
        // Remap asset IDs cleanly so projects remain independent
        if (assets.isNotEmpty()) {
            mediaAssetDao.deleteAssetsForProject(newId)
            val idMap = mutableMapOf<String, String>()
            assets.forEach { old ->
                val mappedId = UUID.randomUUID().toString()
                idMap[old.id] = mappedId
                mediaAssetDao.upsertAsset(old.copy(id = mappedId, projectId = newId, importedAtMs = now))
            }
            val remappedClips = duplicated.timeline.clips.map { clip ->
                clip.copy(assetId = clip.assetId?.let { idMap[it] ?: it })
            }
            projectDao.upsertProject(
                duplicated.copy(timeline = duplicated.timeline.copy(clips = remappedClips)).toEntity()
            )
        }
        return duplicated
    }

    suspend fun deleteProject(projectId: String) {
        projectDao.deleteProject(projectId)
        mediaAssetDao.deleteAssetsForProject(projectId)
        editHistoryDao.deleteHistoryForProject(projectId)
    }

    fun observeAssets(projectId: String): Flow<List<MediaAsset>> =
        mediaAssetDao.observeAssetsForProject(projectId).map { list -> list.map { it.toDomain() } }

    suspend fun getAssets(projectId: String): List<MediaAsset> =
        mediaAssetDao.getAssetsForProject(projectId).map { it.toDomain() }

    suspend fun addAsset(asset: MediaAsset) {
        mediaAssetDao.upsertAsset(asset.toEntity())
    }

    suspend fun updateAssetTags(asset: MediaAsset, newTags: String, newName: String? = null) {
        mediaAssetDao.upsertAsset(
            asset.copy(
                tags = newTags.trim(),
                displayName = newName?.trim()?.ifBlank { asset.displayName } ?: asset.displayName
            ).toEntity()
        )
    }

    suspend fun deleteAsset(assetId: String) {
        mediaAssetDao.deleteAsset(assetId)
    }

    fun observeEditHistory(projectId: String): Flow<List<EditHistoryRecord>> =
        editHistoryDao.observeHistoryForProject(projectId).map { list ->
            list.map {
                EditHistoryRecord(
                    id = it.id,
                    description = it.description,
                    timestampMs = it.timestampMs
                )
            }
        }

    suspend fun recordHistory(projectId: String, description: String, timeline: TimelineDocument) {
        editHistoryDao.insertHistory(
            EditHistoryEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                description = description,
                timelineSnapshotJson = JsonMappers.serializeTimeline(timeline),
                timestampMs = System.currentTimeMillis()
            )
        )
    }

    private fun ProjectEntity.toDomain(): VideoProject = VideoProject(
        id = id,
        title = title,
        aspectRatio = runCatching { AspectRatioPreset.valueOf(aspectRatioName) }
            .getOrDefault(AspectRatioPreset.RATIO_16_9),
        targetResolution = runCatching { ExportResolution.valueOf(targetResolutionName) }
            .getOrDefault(ExportResolution.RES_1080P),
        targetFps = runCatching { FrameRateOption.valueOf(targetFpsName) }
            .getOrDefault(FrameRateOption.FPS_30),
        autosaveEnabled = autosaveEnabled,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs,
        timeline = JsonMappers.deserializeTimeline(timelineJson)
    )

    private fun VideoProject.toEntity(): ProjectEntity = ProjectEntity(
        id = id,
        title = title,
        aspectRatioName = aspectRatio.name,
        targetResolutionName = targetResolution.name,
        targetFpsName = targetFps.name,
        autosaveEnabled = autosaveEnabled,
        timelineJson = JsonMappers.serializeTimeline(timeline),
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs
    )

    private fun MediaAssetEntity.toDomain(): MediaAsset = MediaAsset(
        id = id,
        projectId = projectId,
        uriString = uriString,
        displayName = displayName,
        type = runCatching { MediaAssetType.valueOf(typeName) }.getOrDefault(MediaAssetType.IMAGE),
        mimeType = mimeType,
        durationMs = durationMs,
        width = width,
        height = height,
        fileSizeBytes = fileSizeBytes,
        tags = tags,
        waveformPeaks = JsonMappers.deserializePeaks(waveformPeaksJson),
        thumbnailPath = thumbnailPath,
        importedAtMs = importedAtMs
    )

    private fun MediaAsset.toEntity(): MediaAssetEntity = MediaAssetEntity(
        id = id,
        projectId = projectId,
        uriString = uriString,
        displayName = displayName,
        typeName = type.name,
        mimeType = mimeType,
        durationMs = durationMs,
        width = width,
        height = height,
        fileSizeBytes = fileSizeBytes,
        tags = tags,
        waveformPeaksJson = JsonMappers.serializePeaks(waveformPeaks),
        thumbnailPath = thumbnailPath,
        importedAtMs = importedAtMs
    )
}

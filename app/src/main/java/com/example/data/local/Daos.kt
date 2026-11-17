package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAtMs DESC")
    fun observeAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :projectId LIMIT 1")
    fun observeProjectById(projectId: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :projectId LIMIT 1")
    suspend fun getProjectById(projectId: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteProject(projectId: String)
}

@Dao
interface MediaAssetDao {
    @Query("SELECT * FROM media_assets WHERE projectId = :projectId ORDER BY importedAtMs DESC")
    fun observeAssetsForProject(projectId: String): Flow<List<MediaAssetEntity>>

    @Query("SELECT * FROM media_assets WHERE projectId = :projectId ORDER BY importedAtMs DESC")
    suspend fun getAssetsForProject(projectId: String): List<MediaAssetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAsset(asset: MediaAssetEntity)

    @Query("DELETE FROM media_assets WHERE id = :assetId")
    suspend fun deleteAsset(assetId: String)

    @Query("DELETE FROM media_assets WHERE projectId = :projectId")
    suspend fun deleteAssetsForProject(projectId: String)
}

@Dao
interface EditHistoryDao {
    @Query("SELECT * FROM edit_history WHERE projectId = :projectId ORDER BY timestampMs DESC LIMIT 50")
    fun observeHistoryForProject(projectId: String): Flow<List<EditHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: EditHistoryEntity)

    @Query("DELETE FROM edit_history WHERE projectId = :projectId")
    suspend fun deleteHistoryForProject(projectId: String)
}

@Dao
interface AiProviderDao {
    @Query("SELECT * FROM ai_provider_profiles ORDER BY isActive DESC, providerName ASC")
    fun observeAllProfiles(): Flow<List<AiProviderProfileEntity>>

    @Query("SELECT * FROM ai_provider_profiles WHERE isActive = 1 LIMIT 1")
    fun observeActiveProfile(): Flow<AiProviderProfileEntity?>

    @Query("SELECT * FROM ai_provider_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProfile(): AiProviderProfileEntity?

    @Query("SELECT * FROM ai_provider_profiles WHERE id = :profileId LIMIT 1")
    suspend fun getProfileById(profileId: String): AiProviderProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: AiProviderProfileEntity)

    @Query("UPDATE ai_provider_profiles SET isActive = 0")
    suspend fun clearAllActive()

    @Query("UPDATE ai_provider_profiles SET isActive = 1 WHERE id = :profileId")
    suspend fun markActive(profileId: String)

    @Query("DELETE FROM ai_provider_profiles WHERE id = :profileId")
    suspend fun deleteProfile(profileId: String)
}

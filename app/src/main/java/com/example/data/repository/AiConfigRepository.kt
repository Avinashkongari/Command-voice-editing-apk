package com.example.data.repository

import com.example.data.local.AiProviderDao
import com.example.data.local.AiProviderProfileEntity
import com.example.data.local.JsonMappers
import com.example.domain.model.AiModelInfo
import com.example.domain.model.AiProviderProfile
import com.example.domain.model.AiProviderType
import com.example.security.KeystoreCredentialManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class AiConfigRepository(
    private val aiProviderDao: AiProviderDao,
    private val credentialManager: KeystoreCredentialManager
) {
    fun observeAllProfiles(): Flow<List<AiProviderProfile>> =
        aiProviderDao.observeAllProfiles().map { list -> list.map { it.toDomain() } }

    fun observeActiveProfile(): Flow<AiProviderProfile?> =
        aiProviderDao.observeActiveProfile().map { it?.toDomain() }

    suspend fun getActiveProfile(): AiProviderProfile? =
        aiProviderDao.getActiveProfile()?.toDomain()

    suspend fun getDecryptedApiKey(profileId: String): String? =
        credentialManager.getApiKey(profileId)

    fun getMaskedApiKey(profileId: String): String =
        credentialManager.getMaskedPreview(profileId)

    suspend fun saveProfile(
        profile: AiProviderProfile,
        newRawApiKey: String?,
        setAsActive: Boolean = true
    ): AiProviderProfile {
        val id = profile.id.ifBlank { UUID.randomUUID().toString() }
        if (!newRawApiKey.isNullOrBlank()) {
            credentialManager.saveApiKey(id, newRawApiKey)
        }
        if (setAsActive) {
            aiProviderDao.clearAllActive()
        }
        val stored = profile.copy(
            id = id,
            isActive = setAsActive,
            hasStoredApiKey = credentialManager.hasApiKey(id)
        )
        aiProviderDao.upsertProfile(stored.toEntity())
        return stored
    }

    suspend fun setActiveProfile(profileId: String) {
        aiProviderDao.clearAllActive()
        aiProviderDao.markActive(profileId)
    }

    suspend fun updateSelectedModel(profileId: String, modelId: String, models: List<AiModelInfo>? = null) {
        val existing = aiProviderDao.getProfileById(profileId) ?: return
        aiProviderDao.upsertProfile(
            existing.copy(
                selectedModelId = modelId.trim(),
                availableModelsJson = if (models != null) {
                    JsonMappers.serializeModels(models)
                } else {
                    existing.availableModelsJson
                }
            )
        )
    }

    suspend fun updateConnectionStatus(
        profileId: String,
        testedOk: Boolean,
        message: String,
        models: List<AiModelInfo>? = null
    ) {
        val existing = aiProviderDao.getProfileById(profileId) ?: return
        aiProviderDao.upsertProfile(
            existing.copy(
                isConnectionTested = testedOk,
                lastTestMessage = message,
                lastTestedAtMs = System.currentTimeMillis(),
                availableModelsJson = if (models != null) {
                    JsonMappers.serializeModels(models)
                } else {
                    existing.availableModelsJson
                }
            )
        )
    }

    suspend fun deleteCredential(profileId: String) {
        credentialManager.deleteApiKey(profileId)
        val existing = aiProviderDao.getProfileById(profileId) ?: return
        aiProviderDao.upsertProfile(
            existing.copy(
                isConnectionTested = false,
                lastTestMessage = "API key deleted"
            )
        )
    }

    suspend fun deleteProfile(profileId: String) {
        credentialManager.deleteApiKey(profileId)
        aiProviderDao.deleteProfile(profileId)
    }

    private fun AiProviderProfileEntity.toDomain(): AiProviderProfile = AiProviderProfile(
        id = id,
        providerName = providerName,
        providerType = runCatching { AiProviderType.valueOf(providerTypeName) }
            .getOrDefault(AiProviderType.GEMINI),
        baseUrl = baseUrl,
        selectedModelId = selectedModelId,
        customChatPath = customChatPath,
        customAuthHeader = customAuthHeader,
        availableModels = JsonMappers.deserializeModels(availableModelsJson),
        isConnectionTested = isConnectionTested,
        lastTestMessage = lastTestMessage,
        lastTestedAtMs = lastTestedAtMs,
        isActive = isActive,
        hasStoredApiKey = credentialManager.hasApiKey(id)
    )

    private fun AiProviderProfile.toEntity(): AiProviderProfileEntity = AiProviderProfileEntity(
        id = id,
        providerName = providerName,
        providerTypeName = providerType.name,
        baseUrl = baseUrl.trim().trimEnd('/'),
        selectedModelId = selectedModelId.trim(),
        customChatPath = customChatPath.trim(),
        customAuthHeader = customAuthHeader.trim(),
        availableModelsJson = JsonMappers.serializeModels(availableModels),
        isConnectionTested = isConnectionTested,
        lastTestMessage = lastTestMessage,
        lastTestedAtMs = lastTestedAtMs,
        isActive = isActive
    )
}

package com.example.ai.adapter

import com.example.domain.model.AiModelInfo
import com.example.domain.model.AiProviderProfile

sealed interface AdapterResult<out T> {
    data class Success<T>(val data: T) : AdapterResult<T>
    data class Error(val userMessage: String, val httpCode: Int? = null) : AdapterResult<Nothing>
}

data class ConnectionTestResult(
    val success: Boolean,
    val message: String,
    val latencyMs: Long
)

interface AiProviderAdapter {
    suspend fun fetchModels(profile: AiProviderProfile, apiKey: String): AdapterResult<List<AiModelInfo>>
    suspend fun testConnection(profile: AiProviderProfile, apiKey: String): AdapterResult<ConnectionTestResult>
    suspend fun generateEditingResponse(
        profile: AiProviderProfile,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String
    ): AdapterResult<String>
}

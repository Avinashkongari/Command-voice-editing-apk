package com.example.ai.adapter

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.domain.model.AiModelInfo
import com.example.domain.model.AiProviderProfile
import com.example.domain.model.AiProviderType
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AiProviderRouter(private val context: Context) {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val geminiAdapter = GeminiProviderAdapter(httpClient)
    private val openAiAdapter = OpenAiCompatibleAdapter(httpClient)
    private val customAdapter = CustomEndpointAdapter(httpClient)

    private fun adapterFor(type: AiProviderType): AiProviderAdapter = when (type) {
        AiProviderType.GEMINI -> geminiAdapter
        AiProviderType.OPENAI_COMPATIBLE -> openAiAdapter
        AiProviderType.CUSTOM -> customAdapter
    }

    private fun hasNetwork(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun fetchModels(profile: AiProviderProfile, apiKey: String): AdapterResult<List<AiModelInfo>> {
        if (!hasNetwork()) return AdapterResult.Error("No active internet connection.")
        if (apiKey.isBlank()) return AdapterResult.Error("API key is required.")
        return adapterFor(profile.providerType).fetchModels(profile, apiKey)
    }

    suspend fun testConnection(profile: AiProviderProfile, apiKey: String): AdapterResult<ConnectionTestResult> {
        if (!hasNetwork()) return AdapterResult.Error("No active internet connection.")
        if (apiKey.isBlank()) return AdapterResult.Error("API key is required.")
        return adapterFor(profile.providerType).testConnection(profile, apiKey)
    }

    suspend fun generateEditingResponse(
        profile: AiProviderProfile,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String
    ): AdapterResult<String> {
        if (!hasNetwork()) return AdapterResult.Error("No active internet connection.")
        if (!profile.isConnectionTested) {
            return AdapterResult.Error("AI provider connection has not been tested successfully yet.")
        }
        if (apiKey.isBlank()) return AdapterResult.Error("Missing stored API key.")
        return adapterFor(profile.providerType).generateEditingResponse(profile, apiKey, systemPrompt, userPrompt)
    }
}

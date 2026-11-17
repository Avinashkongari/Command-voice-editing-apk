package com.example.ai.adapter

import com.example.domain.model.AiModelInfo
import com.example.domain.model.AiProviderProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class OpenAiCompatibleAdapter(
    private val httpClient: OkHttpClient
) : AiProviderAdapter {

    override suspend fun fetchModels(
        profile: AiProviderProfile,
        apiKey: String
    ): AdapterResult<List<AiModelInfo>> = withContext(Dispatchers.IO) {
        val base = profile.baseUrl.trimEnd('/')
        if (base.isBlank()) return@withContext AdapterResult.Error("API Base URL is required.")
        val url = "$base/models"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()

        executeAndMapErrors {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@executeAndMapErrors mapHttpError(response.code, body)
                }
                val root = JSONObject(body)
                val data = root.optJSONArray("data") ?: JSONArray()
                val parsed = buildList {
                    for (i in 0 until data.length()) {
                        val obj = data.optJSONObject(i) ?: continue
                        val id = obj.optString("id", "")
                        if (id.isBlank()) continue
                        val owner = obj.optString("owned_by", "")
                        val contextLen = obj.optInt("context_window", obj.optInt("context_length", 0))
                        val cap = buildString {
                            if (owner.isNotBlank()) append(owner)
                            if (contextLen > 0) {
                                if (isNotEmpty()) append(" • ")
                                append("${contextLen / 1000}K ctx")
                            }
                        }
                        add(AiModelInfo(id = id, displayName = id, capabilities = cap))
                    }
                }
                if (parsed.isEmpty()) {
                    AdapterResult.Error("Model list was empty. You can enter a Model ID manually.")
                } else {
                    AdapterResult.Success(parsed.sortedBy { it.id })
                }
            }
        }
    }

    override suspend fun testConnection(
        profile: AiProviderProfile,
        apiKey: String
    ): AdapterResult<ConnectionTestResult> {
        val start = System.currentTimeMillis()
        val modelId = profile.selectedModelId.trim()
        if (modelId.isBlank()) {
            return AdapterResult.Error("Please select or enter a Model ID before testing.")
        }
        val result = generateEditingResponse(
            profile = profile,
            apiKey = apiKey,
            systemPrompt = "Respond with JSON: {\"status\":\"ok\"}",
            userPrompt = "Ping"
        )
        val latency = System.currentTimeMillis() - start
        return when (result) {
            is AdapterResult.Success -> AdapterResult.Success(
                ConnectionTestResult(
                    success = true,
                    message = "Connected to ${profile.providerName} ($modelId) in ${latency}ms",
                    latencyMs = latency
                )
            )
            is AdapterResult.Error -> result
        }
    }

    override suspend fun generateEditingResponse(
        profile: AiProviderProfile,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String
    ): AdapterResult<String> = withContext(Dispatchers.IO) {
        val base = profile.baseUrl.trimEnd('/')
        val modelId = profile.selectedModelId.trim()
        if (base.isBlank() || modelId.isBlank()) {
            return@withContext AdapterResult.Error("Base URL and Model ID are required.")
        }

        val payload = JSONObject().apply {
            put("model", modelId)
            put("temperature", 0.2)
            put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", userPrompt))
            )
        }

        val url = "$base/chat/completions"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        executeAndMapErrors {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@executeAndMapErrors mapHttpError(response.code, body)
                }
                val root = JSONObject(body)
                val choices = root.optJSONArray("choices")
                val content = choices?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?.trim()
                if (content.isNullOrBlank()) {
                    AdapterResult.Error("Provider returned an empty completion.")
                } else {
                    AdapterResult.Success(content)
                }
            }
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

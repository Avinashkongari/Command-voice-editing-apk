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

class CustomEndpointAdapter(
    private val httpClient: OkHttpClient
) : AiProviderAdapter {

    override suspend fun fetchModels(
        profile: AiProviderProfile,
        apiKey: String
    ): AdapterResult<List<AiModelInfo>> = withContext(Dispatchers.IO) {
        val base = profile.baseUrl.trimEnd('/')
        if (base.isBlank()) return@withContext AdapterResult.Error("API Base URL is required.")
        val url = "$base/models"
        val authHeaderName = profile.customAuthHeader.ifBlank { "Authorization" }
        val authValue = if (authHeaderName.equals("Authorization", ignoreCase = true)) {
            "Bearer $apiKey"
        } else {
            apiKey
        }
        val request = Request.Builder()
            .url(url)
            .header(authHeaderName, authValue)
            .get()
            .build()

        executeAndMapErrors {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@executeAndMapErrors AdapterResult.Error(
                        "Model discovery not supported or returned HTTP ${response.code}. Enter Model ID manually.",
                        response.code
                    )
                }
                val root = JSONObject(body)
                val array = root.optJSONArray("data") ?: root.optJSONArray("models") ?: JSONArray()
                val parsed = buildList {
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        val id = obj.optString("id", obj.optString("name", ""))
                        if (id.isBlank()) continue
                        add(AiModelInfo(id = id, displayName = id, capabilities = "Custom endpoint"))
                    }
                }
                if (parsed.isEmpty()) {
                    AdapterResult.Error("No models discovered from custom endpoint. Enter Model ID manually.")
                } else {
                    AdapterResult.Success(parsed)
                }
            }
        }
    }

    override suspend fun testConnection(
        profile: AiProviderProfile,
        apiKey: String
    ): AdapterResult<ConnectionTestResult> {
        val start = System.currentTimeMillis()
        val res = generateEditingResponse(
            profile = profile,
            apiKey = apiKey,
            systemPrompt = "Reply with OK.",
            userPrompt = "Ping"
        )
        val latency = System.currentTimeMillis() - start
        return when (res) {
            is AdapterResult.Success -> AdapterResult.Success(
                ConnectionTestResult(
                    success = true,
                    message = "Custom endpoint responded in ${latency}ms",
                    latencyMs = latency
                )
            )
            is AdapterResult.Error -> res
        }
    }

    override suspend fun generateEditingResponse(
        profile: AiProviderProfile,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String
    ): AdapterResult<String> = withContext(Dispatchers.IO) {
        val base = profile.baseUrl.trimEnd('/')
        val path = profile.customChatPath.ifBlank { "/chat/completions" }.let {
            if (it.startsWith("/")) it else "/$it"
        }
        val modelId = profile.selectedModelId.trim()
        if (base.isBlank() || modelId.isBlank()) {
            return@withContext AdapterResult.Error("Base URL and Model ID are required.")
        }

        val authHeaderName = profile.customAuthHeader.ifBlank { "Authorization" }
        val authValue = if (authHeaderName.equals("Authorization", ignoreCase = true)) {
            "Bearer $apiKey"
        } else {
            apiKey
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

        val request = Request.Builder()
            .url("$base$path")
            .header(authHeaderName, authValue)
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
                val text = choices?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?: root.optString("output_text")
                        .ifBlank { root.optString("response") }
                if (text.isNullOrBlank()) {
                    AdapterResult.Error("Custom endpoint returned unrecognized or empty body.")
                } else {
                    AdapterResult.Success(text.trim())
                }
            }
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

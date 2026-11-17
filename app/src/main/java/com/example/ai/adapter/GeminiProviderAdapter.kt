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
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class GeminiProviderAdapter(
    private val httpClient: OkHttpClient
) : AiProviderAdapter {

    override suspend fun fetchModels(
        profile: AiProviderProfile,
        apiKey: String
    ): AdapterResult<List<AiModelInfo>> = withContext(Dispatchers.IO) {
        val base = profile.baseUrl.trimEnd('/').ifBlank {
            "https://generativelanguage.googleapis.com/v1beta"
        }
        val url = "$base/models"
        val request = Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey)
            .get()
            .build()

        executeAndMapErrors {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@executeAndMapErrors mapHttpError(response.code, body)
                }
                val root = JSONObject(body)
                val modelsJson = root.optJSONArray("models") ?: JSONArray()
                val parsed = buildList {
                    for (i in 0 until modelsJson.length()) {
                        val obj = modelsJson.optJSONObject(i) ?: continue
                        val fullName = obj.optString("name", "")
                        val cleanId = fullName.removePrefix("models/")
                        if (cleanId.isBlank()) continue
                        val methods = obj.optJSONArray("supportedGenerationMethods")
                        val methodList = buildList {
                            if (methods != null) {
                                for (m in 0 until methods.length()) {
                                    add(methods.optString(m))
                                }
                            }
                        }
                        if (methodList.isNotEmpty() && !methodList.contains("generateContent")) continue
                        val displayName = obj.optString("displayName", cleanId)
                        val inputLimit = obj.optInt("inputTokenLimit", 0)
                        val capText = buildString {
                            if (inputLimit > 0) append("${inputLimit / 1000}K ctx")
                            if (methodList.isNotEmpty()) {
                                if (isNotEmpty()) append(" • ")
                                append(methodList.joinToString(", "))
                            }
                        }
                        add(AiModelInfo(id = cleanId, displayName = displayName, capabilities = capText))
                    }
                }
                if (parsed.isEmpty()) {
                    AdapterResult.Error("No text/multimodal generation models returned by endpoint.")
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
        val modelId = profile.selectedModelId.trim().removePrefix("models/")
        if (modelId.isBlank()) {
            return AdapterResult.Error("Please fetch & select a Model ID (or enter one manually) before testing.")
        }
        val result = generateEditingResponse(
            profile = profile,
            apiKey = apiKey,
            systemPrompt = "Respond with exactly: OK",
            userPrompt = "Ping"
        )
        val latency = System.currentTimeMillis() - start
        return when (result) {
            is AdapterResult.Success -> AdapterResult.Success(
                ConnectionTestResult(
                    success = true,
                    message = "Connected to Gemini ($modelId) in ${latency}ms",
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
        val base = profile.baseUrl.trimEnd('/').ifBlank {
            "https://generativelanguage.googleapis.com/v1beta"
        }
        val modelId = profile.selectedModelId.trim().removePrefix("models/")
        if (modelId.isBlank()) {
            return@withContext AdapterResult.Error("No model selected.")
        }

        val payload = JSONObject().apply {
            put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemPrompt))
                )
            )
            put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))
                )
            )
            put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.2)
                    .put("responseMimeType", "application/json")
            )
        }

        val url = "$base/models/$modelId:generateContent"
        val request = Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey)
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
                val candidates = root.optJSONArray("candidates")
                val first = candidates?.optJSONObject(0)
                val parts = first?.optJSONObject("content")?.optJSONArray("parts")
                val text = parts?.optJSONObject(0)?.optString("text")?.trim()
                if (text.isNullOrBlank()) {
                    AdapterResult.Error("Gemini returned an empty response.")
                } else {
                    AdapterResult.Success(text)
                }
            }
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

internal inline fun <T> executeAndMapErrors(block: () -> AdapterResult<T>): AdapterResult<T> {
    return try {
        block()
    } catch (_: UnknownHostException) {
        AdapterResult.Error("Network error: Cannot reach provider host. Check internet connection or Base URL.")
    } catch (_: SocketTimeoutException) {
        AdapterResult.Error("Request timed out waiting for AI provider response.")
    } catch (e: IOException) {
        AdapterResult.Error("Network error: ${e.message ?: "Connection failed"}")
    } catch (e: Exception) {
        AdapterResult.Error("Unexpected provider error: ${e.message ?: "Unknown error"}")
    }
}

internal fun mapHttpError(code: Int, rawBody: String): AdapterResult.Error {
    val apiMessage = runCatching {
        val obj = JSONObject(rawBody)
        obj.optJSONObject("error")?.optString("message")
            ?: obj.optString("message")
    }.getOrNull()?.takeIf { it.isNotBlank() }

    val friendly = when (code) {
        401, 403 -> "Invalid or unauthorized API key ($code). Please verify your credentials."
        404 -> "Endpoint or model not found (404). Check Base URL and Model ID."
        429 -> "Rate limit or quota exceeded (429). Please wait or switch models."
        400 -> "Bad request (400): ${apiMessage ?: "Unsupported model or parameters"}"
        in 500..599 -> "Provider server error ($code). Try again shortly."
        else -> "Provider returned HTTP $code"
    }
    val detail = if (!apiMessage.isNullOrBlank() && code != 400) "$friendly — $apiMessage" else friendly
    return AdapterResult.Error(detail, code)
}

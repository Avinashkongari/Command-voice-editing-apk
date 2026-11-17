package com.example.ai.engine

import com.example.data.local.JsonMappers
import com.example.domain.model.AiCommandEnvelope
import com.example.domain.model.AiEditorOperation
import com.squareup.moshi.Types
import org.json.JSONObject

object AiResponseParser {

    fun parse(rawResponse: String): Result<AiCommandEnvelope> {
        val cleaned = extractJsonObject(rawResponse)
        if (cleaned.isBlank()) {
            return Result.failure(IllegalArgumentException("AI returned non-JSON output."))
        }

        // Standard envelope parse
        val direct = JsonMappers.parseCommandEnvelope(cleaned)
        if (direct != null && (direct.operations.isNotEmpty() || direct.reply.isNotBlank())) {
            return Result.success(direct)
        }

        // Fallback: check if model returned a single bare operation JSON object (like {"operation":"add_image", ...})
        return runCatching {
            val root = JSONObject(cleaned)
            if (root.has("operation")) {
                val singleAdapter = JsonMappers.moshi.adapter(AiEditorOperation::class.java)
                val singleOp = singleAdapter.fromJson(cleaned)
                    ?: throw IllegalArgumentException("Could not parse operation object.")
                AiCommandEnvelope(
                    reply = "Command ready.",
                    status_preview = "Applying ${singleOp.operation}...",
                    operations = listOf(singleOp)
                )
            } else {
                val opsArray = root.optJSONArray("operations")?.toString() ?: "[]"
                val listType = Types.newParameterizedType(List::class.java, AiEditorOperation::class.java)
                val opsAdapter = JsonMappers.moshi.adapter<List<AiEditorOperation>>(listType)
                val ops = opsAdapter.fromJson(opsArray).orEmpty()
                AiCommandEnvelope(
                    reply = root.optString("reply", ""),
                    status_preview = root.optString("status_preview", ""),
                    operations = ops
                )
            }
        }
    }

    private fun extractJsonObject(raw: String): String {
        val trimmed = raw.trim()
        val fenceStripped = if (trimmed.startsWith("```")) {
            trimmed
                .removePrefix("```json")
                .removePrefix("```JSON")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
        } else {
            trimmed
        }
        val firstBrace = fenceStripped.indexOf('{')
        val lastBrace = fenceStripped.lastIndexOf('}')
        return if (firstBrace >= 0 && lastBrace > firstBrace) {
            fenceStripped.substring(firstBrace, lastBrace + 1)
        } else {
            fenceStripped
        }
    }
}

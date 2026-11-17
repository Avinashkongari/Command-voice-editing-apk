package com.example.data.local

import com.example.domain.model.AiCommandEnvelope
import com.example.domain.model.AiModelInfo
import com.example.domain.model.TimelineDocument
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object JsonMappers {
    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val timelineAdapter = moshi.adapter(TimelineDocument::class.java)
    private val modelsListType = Types.newParameterizedType(List::class.java, AiModelInfo::class.java)
    private val modelsAdapter = moshi.adapter<List<AiModelInfo>>(modelsListType)
    private val floatListType = Types.newParameterizedType(List::class.java, Float::class.javaObjectType)
    private val floatListAdapter = moshi.adapter<List<Float>>(floatListType)
    private val commandEnvelopeAdapter = moshi.adapter(AiCommandEnvelope::class.java)

    fun serializeTimeline(doc: TimelineDocument): String = timelineAdapter.toJson(doc)

    fun deserializeTimeline(json: String): TimelineDocument =
        runCatching { timelineAdapter.fromJson(json) }.getOrNull() ?: TimelineDocument()

    fun serializeModels(models: List<AiModelInfo>): String = modelsAdapter.toJson(models)

    fun deserializeModels(json: String): List<AiModelInfo> =
        runCatching { modelsAdapter.fromJson(json) }.getOrNull() ?: emptyList()

    fun serializePeaks(peaks: List<Float>): String = floatListAdapter.toJson(peaks)

    fun deserializePeaks(json: String): List<Float> =
        runCatching { floatListAdapter.fromJson(json) }.getOrNull() ?: emptyList()

    fun parseCommandEnvelope(json: String): AiCommandEnvelope? =
        runCatching { commandEnvelopeAdapter.fromJson(json) }.getOrNull()
}

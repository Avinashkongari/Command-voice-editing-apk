package com.example.ai.engine

import com.example.domain.model.AiEditorOperation
import com.example.domain.model.MediaAsset
import com.example.domain.model.MediaAssetType
import com.example.domain.model.TimelineDocument

sealed interface ValidationResult {
    data class Valid(val operations: List<AiEditorOperation>) : ValidationResult
    data class MissingAsset(val assetType: MediaAssetType, val message: String) : ValidationResult
    data class ClarificationNeeded(val question: String) : ValidationResult
    data class Invalid(val reason: String) : ValidationResult
}

object CommandValidator {

    private val supportedOps = setOf(
        "add_image", "add_video", "add_audio",
        "trim", "split", "delete", "move", "resize", "crop", "rotate",
        "duplicate", "freeze_frame", "add_transition", "add_effect",
        "add_text", "add_subtitle", "change_speed", "adjust_volume",
        "set_keyframe", "undo", "redo", "request_asset", "clarify"
    )

    fun validate(
        operations: List<AiEditorOperation>,
        timeline: TimelineDocument,
        assets: List<MediaAsset>,
        playheadMs: Long,
        selectedClipId: String?,
        selectedAssetId: String?
    ): ValidationResult {
        if (operations.isEmpty()) {
            return ValidationResult.Invalid("AI did not return any actionable editing operation.")
        }

        val normalized = mutableListOf<AiEditorOperation>()
        for (op in operations) {
            val opName = op.operation.lowercase().trim()
            if (opName !in supportedOps) {
                return ValidationResult.Invalid("Unsupported editing operation: '$opName'.")
            }

            if (opName == "request_asset") {
                val type = when (op.missing_asset_type?.uppercase()) {
                    "VIDEO" -> MediaAssetType.VIDEO
                    "AUDIO" -> MediaAssetType.AUDIO
                    else -> MediaAssetType.IMAGE
                }
                val hint = op.missing_asset_hint?.takeIf { it.isNotBlank() }
                    ?: "Matching ${type.name.lowercase()} asset nahi mila. Please Gallery/Files se select karo."
                return ValidationResult.MissingAsset(type, hint)
            }

            if (opName == "clarify") {
                return ValidationResult.ClarificationNeeded(
                    op.question?.takeIf { it.isNotBlank() }
                        ?: "Kripya batayein ki yeh edit kis timestamp ya clip par apply karna hai?"
                )
            }

            if (opName in setOf("add_image", "add_video", "add_audio")) {
                val expectedType = when (opName) {
                    "add_video" -> MediaAssetType.VIDEO
                    "add_audio" -> MediaAssetType.AUDIO
                    else -> MediaAssetType.IMAGE
                }
                val resolvedAsset = resolveAsset(op.asset_id, op.asset_query, expectedType, assets, selectedAssetId)
                if (resolvedAsset == null) {
                    val label = when (expectedType) {
                        MediaAssetType.IMAGE -> "image"
                        MediaAssetType.VIDEO -> "video"
                        MediaAssetType.AUDIO, MediaAssetType.VOICEOVER -> "audio file"
                    }
                    return ValidationResult.MissingAsset(
                        expectedType,
                        "Matching $label nahi mili. Please select it from your device."
                    )
                }

                val startMs = (op.start_ms ?: playheadMs).coerceAtLeast(0L)
                val defaultDur = when (resolvedAsset.type) {
                    MediaAssetType.IMAGE -> timeline.defaultImageDurationMs
                    else -> resolvedAsset.durationMs.coerceAtLeast(3000L)
                }
                val endMs = op.end_ms ?: (startMs + (op.duration_ms ?: defaultDur))
                if (endMs <= startMs) {
                    return ValidationResult.Invalid("Invalid timestamps: end time ($endMs ms) must be after start time ($startMs ms).")
                }
                normalized.add(op.copy(asset_id = resolvedAsset.id, start_ms = startMs, end_ms = endMs))
                continue
            }

            if (opName in setOf("add_text", "add_subtitle")) {
                if (op.text.isNullOrBlank()) {
                    return ValidationResult.ClarificationNeeded("Text/Subtitle ke liye kya likhna hai?")
                }
                val startMs = (op.start_ms ?: playheadMs).coerceAtLeast(0L)
                val endMs = op.end_ms ?: (startMs + (op.duration_ms ?: 4000L))
                if (endMs <= startMs) {
                    return ValidationResult.Invalid("Invalid text timestamps.")
                }
                normalized.add(op.copy(start_ms = startMs, end_ms = endMs))
                continue
            }

            if (opName in setOf("undo", "redo")) {
                normalized.add(op)
                continue
            }

            // Clip-targeting operations
            val targetClipId = resolveClipId(op.clip_id, timeline, playheadMs, selectedClipId)
            if (targetClipId == null) {
                return ValidationResult.Invalid("No target clip found on the timeline for '$opName'. Please select a clip or move the playhead over one.")
            }
            val clip = timeline.clips.first { it.id == targetClipId }

            if (opName == "split") {
                val splitAt = op.split_ms ?: op.start_ms ?: playheadMs
                if (splitAt <= clip.startMs + 100L || splitAt >= clip.endMs - 100L) {
                    return ValidationResult.Invalid("Playhead/split timestamp ($splitAt ms) must lie inside the selected clip (${clip.startMs}ms - ${clip.endMs}ms).")
                }
                normalized.add(op.copy(clip_id = targetClipId, split_ms = splitAt))
                continue
            }

            if (opName == "change_speed") {
                val speed = op.speed ?: 1f
                if (speed < 0.25f || speed > 8f) {
                    return ValidationResult.Invalid("Speed must be between 0.25x and 8.0x.")
                }
            }

            if (opName == "adjust_volume") {
                val vol = op.volume ?: 1f
                if (vol < 0f || vol > 2f) {
                    return ValidationResult.Invalid("Volume must be between 0% and 200%.")
                }
            }

            if (opName == "move" || opName == "resize") {
                val newStart = op.start_ms ?: clip.startMs
                val newEnd = op.end_ms ?: (newStart + (op.duration_ms ?: clip.durationMs))
                if (newStart < 0L || newEnd <= newStart) {
                    return ValidationResult.Invalid("Invalid clip bounds ($newStart ms to $newEnd ms).")
                }
            }

            normalized.add(op.copy(clip_id = targetClipId))
        }

        return ValidationResult.Valid(normalized)
    }

    private fun resolveAsset(
        assetId: String?,
        assetQuery: String?,
        expectedType: MediaAssetType,
        assets: List<MediaAsset>,
        selectedAssetId: String?
    ): MediaAsset? {
        if (!assetId.isNullOrBlank()) {
            assets.firstOrNull { it.id == assetId }?.let { return it }
        }
        if (!assetQuery.isNullOrBlank()) {
            val q = assetQuery.lowercase()
            assets.firstOrNull {
                (it.type == expectedType || (expectedType == MediaAssetType.AUDIO && it.type == MediaAssetType.VOICEOVER)) &&
                    (it.displayName.lowercase().contains(q) || it.tags.lowercase().contains(q))
            }?.let { return it }
        }
        if (!selectedAssetId.isNullOrBlank()) {
            val selected = assets.firstOrNull { it.id == selectedAssetId }
            if (selected != null && (selected.type == expectedType ||
                    (expectedType == MediaAssetType.AUDIO && selected.type == MediaAssetType.VOICEOVER))
            ) {
                return selected
            }
        }
        val candidates = assets.filter {
            it.type == expectedType || (expectedType == MediaAssetType.AUDIO && it.type == MediaAssetType.VOICEOVER)
        }
        return if (candidates.size == 1) candidates.first() else null
    }

    private fun resolveClipId(
        requestedClipId: String?,
        timeline: TimelineDocument,
        playheadMs: Long,
        selectedClipId: String?
    ): String? {
        if (!requestedClipId.isNullOrBlank()) {
            timeline.clips.firstOrNull { it.id == requestedClipId }?.let { return it.id }
        }
        if (!selectedClipId.isNullOrBlank()) {
            timeline.clips.firstOrNull { it.id == selectedClipId }?.let { return it.id }
        }
        return timeline.clips.firstOrNull { playheadMs in it.startMs..it.endMs }?.id
            ?: timeline.clips.lastOrNull()?.id
    }
}

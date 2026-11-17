package com.example.engine

import com.example.domain.model.AiEditorOperation
import com.example.domain.model.ClipEffects
import com.example.domain.model.ClipTextStyle
import com.example.domain.model.ColorFilterPreset
import com.example.domain.model.CropRect
import com.example.domain.model.Keyframe
import com.example.domain.model.MediaAsset
import com.example.domain.model.MotionEffectPreset
import com.example.domain.model.TextAnimationPreset
import com.example.domain.model.TimelineClip
import com.example.domain.model.TimelineDocument
import com.example.domain.model.TrackType
import com.example.domain.model.TransitionType
import java.util.UUID
import kotlin.math.roundToLong

data class EngineExecutionResult(
    val document: TimelineDocument,
    val highlightedClipId: String? = null,
    val summary: String
)

/**
 * Deterministic multi-track timeline editing engine with full undo/redo stack.
 */
class TimelineEngine(initialDocument: TimelineDocument = TimelineDocument()) {

    private val undoStack = ArrayDeque<Pair<TimelineDocument, String>>()
    private val redoStack = ArrayDeque<Pair<TimelineDocument, String>>()

    var currentDocument: TimelineDocument = initialDocument
        private set

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun load(document: TimelineDocument) {
        currentDocument = document
        undoStack.clear()
        redoStack.clear()
    }

    private fun commit(next: TimelineDocument, description: String, highlightedClipId: String? = null): EngineExecutionResult {
        undoStack.addLast(currentDocument to description)
        if (undoStack.size > 50) undoStack.removeFirst()
        redoStack.clear()
        currentDocument = next
        return EngineExecutionResult(
            document = next,
            highlightedClipId = highlightedClipId,
            summary = description
        )
    }

    fun undo(): EngineExecutionResult? {
        if (undoStack.isEmpty()) return null
        val (prevDoc, desc) = undoStack.removeLast()
        redoStack.addLast(currentDocument to desc)
        currentDocument = prevDoc
        return EngineExecutionResult(prevDoc, null, "Undid: $desc")
    }

    fun redo(): EngineExecutionResult? {
        if (redoStack.isEmpty()) return null
        val (nextDoc, desc) = redoStack.removeLast()
        undoStack.addLast(currentDocument to desc)
        currentDocument = nextDoc
        return EngineExecutionResult(nextDoc, null, "Redid: $desc")
    }

    fun addAssetClip(
        asset: MediaAsset,
        trackType: TrackType,
        startMs: Long,
        endMs: Long
    ): EngineExecutionResult {
        val safeStart = snapIfEnabled(startMs.coerceAtLeast(0L), ignoreClipId = null)
        val duration = (endMs - startMs).coerceAtLeast(500L)
        val safeEnd = safeStart + duration
        val newClip = TimelineClip(
            id = UUID.randomUUID().toString(),
            trackType = trackType,
            assetId = asset.id,
            label = asset.displayName,
            startMs = safeStart,
            endMs = safeEnd,
            sourceStartMs = 0L,
            sourceEndMs = if (asset.durationMs > 0) minOf(duration, asset.durationMs) else duration
        )
        val next = currentDocument.copy(clips = currentDocument.clips + newClip)
        return commit(next, "Added ${asset.displayName} (${formatMs(safeStart)} - ${formatMs(safeEnd)})", newClip.id)
    }

    fun addTextClip(
        text: String,
        isSubtitle: Boolean,
        startMs: Long,
        endMs: Long,
        fontSizeSp: Float = if (isSubtitle) 20f else 28f,
        colorHex: Long = 0xFFFFFFFF,
        x: Float = 0.5f,
        y: Float = if (isSubtitle) 0.86f else 0.5f,
        animation: TextAnimationPreset = TextAnimationPreset.FADE
    ): EngineExecutionResult {
        val safeStart = startMs.coerceAtLeast(0L)
        val safeEnd = endMs.coerceAtLeast(safeStart + 500L)
        val clip = TimelineClip(
            id = UUID.randomUUID().toString(),
            trackType = TrackType.TEXT_SUBTITLE,
            label = if (isSubtitle) "Sub: $text" else text,
            startMs = safeStart,
            endMs = safeEnd,
            textStyle = ClipTextStyle(
                text = text,
                isSubtitle = isSubtitle,
                fontSizeSp = fontSizeSp,
                textColorHex = colorHex,
                positionX = x,
                positionY = y,
                animation = animation
            )
        )
        val next = currentDocument.copy(clips = currentDocument.clips + clip)
        return commit(next, if (isSubtitle) "Added subtitle \"$text\"" else "Added text \"$text\"", clip.id)
    }

    fun splitClip(clipId: String, splitMs: Long): EngineExecutionResult? {
        val target = currentDocument.clips.firstOrNull { it.id == clipId } ?: return null
        if (splitMs <= target.startMs + 100L || splitMs >= target.endMs - 100L) return null
        val leftDuration = splitMs - target.startMs
        val sourceSplit = target.sourceStartMs + (leftDuration * target.speed).roundToLong()

        val left = target.copy(
            endMs = splitMs,
            sourceEndMs = sourceSplit,
            transitionOut = TransitionType.NONE
        )
        val right = target.copy(
            id = UUID.randomUUID().toString(),
            startMs = splitMs,
            sourceStartMs = sourceSplit,
            transitionIn = TransitionType.NONE
        )
        val nextClips = currentDocument.clips.flatMap { if (it.id == clipId) listOf(left, right) else listOf(it) }
        return commit(currentDocument.copy(clips = nextClips), "Split ${target.label} at ${formatMs(splitMs)}", right.id)
    }

    fun deleteClip(clipId: String): EngineExecutionResult? {
        val target = currentDocument.clips.firstOrNull { it.id == clipId } ?: return null
        val next = currentDocument.copy(clips = currentDocument.clips.filterNot { it.id == clipId })
        return commit(next, "Deleted ${target.label}")
    }

    fun duplicateClip(clipId: String): EngineExecutionResult? {
        val target = currentDocument.clips.firstOrNull { it.id == clipId } ?: return null
        val dur = target.durationMs
        val copy = target.copy(
            id = UUID.randomUUID().toString(),
            label = "${target.label} (Copy)",
            startMs = target.endMs,
            endMs = target.endMs + dur
        )
        return commit(currentDocument.copy(clips = currentDocument.clips + copy), "Duplicated ${target.label}", copy.id)
    }

    fun updateClip(clipId: String, description: String, transform: (TimelineClip) -> TimelineClip): EngineExecutionResult? {
        val exists = currentDocument.clips.any { it.id == clipId }
        if (!exists) return null
        val nextClips = currentDocument.clips.map { if (it.id == clipId) transform(it) else it }
        return commit(currentDocument.copy(clips = nextClips), description, clipId)
    }

    fun moveAndResizeClip(clipId: String, newStartMs: Long, newEndMs: Long): EngineExecutionResult? {
        val safeStart = snapIfEnabled(newStartMs.coerceAtLeast(0L), ignoreClipId = clipId)
        val safeEnd = newEndMs.coerceAtLeast(safeStart + 250L)
        return updateClip(clipId, "Updated timing (${formatMs(safeStart)} - ${formatMs(safeEnd)})") {
            it.copy(startMs = safeStart, endMs = safeEnd)
        }
    }

    fun toggleSnapping(): TimelineDocument {
        currentDocument = currentDocument.copy(snappingEnabled = !currentDocument.snappingEnabled)
        return currentDocument
    }

    fun executeAiOperations(
        operations: List<AiEditorOperation>,
        assets: List<MediaAsset>,
        playheadMs: Long
    ): EngineExecutionResult {
        var lastResult = EngineExecutionResult(currentDocument, null, "No changes applied.")
        for (op in operations) {
            lastResult = executeSingleAiOperation(op, assets, playheadMs)
        }
        return lastResult
    }

    private fun executeSingleAiOperation(
        op: AiEditorOperation,
        assets: List<MediaAsset>,
        playheadMs: Long
    ): EngineExecutionResult {
        return when (op.operation.lowercase()) {
            "undo" -> undo() ?: EngineExecutionResult(currentDocument, null, "Nothing to undo.")
            "redo" -> redo() ?: EngineExecutionResult(currentDocument, null, "Nothing to redo.")
            "add_image", "add_video", "add_audio" -> {
                val asset = assets.first { it.id == op.asset_id }
                val defaultTrack = when (op.operation.lowercase()) {
                    "add_video" -> TrackType.VIDEO_MAIN
                    "add_audio" -> TrackType.AUDIO_MAIN
                    else -> TrackType.IMAGE_OVERLAY
                }
                val track = if (op.track.isNullOrBlank()) defaultTrack else TrackType.fromId(op.track)
                val start = op.start_ms ?: playheadMs
                val end = op.end_ms ?: (start + (op.duration_ms ?: currentDocument.defaultImageDurationMs))
                addAssetClip(asset, track, start, end)
            }
            "add_text", "add_subtitle" -> {
                val isSub = op.operation.equals("add_subtitle", true) || (op.is_subtitle == true)
                val color = parseHexColor(op.text_color) ?: 0xFFFFFFFF
                val anim = op.text_animation?.let {
                    runCatching { TextAnimationPreset.valueOf(it.uppercase()) }.getOrNull()
                } ?: TextAnimationPreset.FADE
                addTextClip(
                    text = op.text.orEmpty(),
                    isSubtitle = isSub,
                    startMs = op.start_ms ?: playheadMs,
                    endMs = op.end_ms ?: ((op.start_ms ?: playheadMs) + 4000L),
                    fontSizeSp = op.font_size ?: if (isSub) 20f else 28f,
                    colorHex = color,
                    x = op.x ?: 0.5f,
                    y = op.y ?: if (isSub) 0.86f else 0.5f,
                    animation = anim
                )
            }
            "split" -> {
                val id = op.clip_id!!
                splitClip(id, op.split_ms ?: playheadMs)
                    ?: EngineExecutionResult(currentDocument, null, "Could not split clip.")
            }
            "delete" -> {
                deleteClip(op.clip_id!!)
                    ?: EngineExecutionResult(currentDocument, null, "Clip not found.")
            }
            "duplicate" -> {
                duplicateClip(op.clip_id!!)
                    ?: EngineExecutionResult(currentDocument, null, "Clip not found.")
            }
            "move", "resize" -> {
                val clip = currentDocument.clips.first { it.id == op.clip_id }
                val newStart = op.start_ms ?: clip.startMs
                val newEnd = op.end_ms ?: (newStart + (op.duration_ms ?: clip.durationMs))
                moveAndResizeClip(clip.id, newStart, newEnd)!!
            }
            "trim" -> {
                val clip = currentDocument.clips.first { it.id == op.clip_id }
                val trimStart = op.trim_start_ms ?: clip.sourceStartMs
                val trimEnd = op.trim_end_ms ?: clip.sourceEndMs
                val newDur = (trimEnd - trimStart).coerceAtLeast(250L)
                updateClip(clip.id, "Trimmed ${clip.label}") {
                    it.copy(
                        sourceStartMs = trimStart,
                        sourceEndMs = trimEnd,
                        endMs = it.startMs + (newDur / it.speed).roundToLong()
                    )
                }!!
            }
            "crop" -> {
                updateClip(op.clip_id!!, "Cropped clip") {
                    it.copy(
                        crop = CropRect(
                            left = (op.crop_left ?: it.crop.left).coerceIn(0f, 0.8f),
                            top = (op.crop_top ?: it.crop.top).coerceIn(0f, 0.8f),
                            right = (op.crop_right ?: it.crop.right).coerceIn(0.2f, 1f),
                            bottom = (op.crop_bottom ?: it.crop.bottom).coerceIn(0.2f, 1f)
                        )
                    )
                }!!
            }
            "rotate" -> {
                val deg = op.rotation_deg ?: 90f
                updateClip(op.clip_id!!, "Rotated ${deg.toInt()}°") {
                    it.copy(rotationDeg = (it.rotationDeg + deg) % 360f)
                }!!
            }
            "change_speed" -> {
                val spd = (op.speed ?: 1f).coerceIn(0.25f, 8f)
                updateClip(op.clip_id!!, "Speed set to ${spd}x") {
                    val baseDur = (it.durationMs * it.speed).roundToLong().coerceAtLeast(300L)
                    val nextDur = (baseDur / spd).roundToLong().coerceAtLeast(250L)
                    it.copy(speed = spd, endMs = it.startMs + nextDur)
                }!!
            }
            "adjust_volume" -> {
                val vol = (op.volume ?: 1f).coerceIn(0f, 2f)
                updateClip(op.clip_id!!, "Volume set to ${(vol * 100).toInt()}%") {
                    it.copy(
                        volume = vol,
                        isMuted = vol == 0f,
                        fadeInMs = op.fade_in_ms ?: it.fadeInMs,
                        fadeOutMs = op.fade_out_ms ?: it.fadeOutMs
                    )
                }!!
            }
            "add_transition" -> {
                val tr = op.transition?.let {
                    runCatching { TransitionType.valueOf(it.uppercase()) }.getOrNull()
                } ?: TransitionType.CROSSFADE
                updateClip(op.clip_id!!, "Added ${tr.displayName} transition") {
                    it.copy(
                        transitionIn = tr,
                        transitionInDurationMs = (op.duration_ms ?: currentDocument.defaultTransitionDurationMs).coerceIn(200L, 2500L)
                    )
                }!!
            }
            "add_effect" -> {
                val effName = op.effect?.lowercase().orEmpty()
                val v = op.value ?: 0.25f
                updateClip(op.clip_id!!, "Applied $effName effect") { clip ->
                    val cur = clip.effects
                    val updated: ClipEffects = when (effName) {
                        "zoom_in" -> cur.copy(motionEffect = MotionEffectPreset.ZOOM_IN)
                        "zoom_out" -> cur.copy(motionEffect = MotionEffectPreset.ZOOM_OUT)
                        "pan_left" -> cur.copy(motionEffect = MotionEffectPreset.PAN_LEFT)
                        "pan_right" -> cur.copy(motionEffect = MotionEffectPreset.PAN_RIGHT)
                        "ken_burns" -> cur.copy(motionEffect = MotionEffectPreset.KEN_BURNS)
                        "brightness" -> cur.copy(brightness = v.coerceIn(-1f, 1f))
                        "contrast" -> cur.copy(contrast = v.coerceIn(-1f, 1f))
                        "saturation" -> cur.copy(saturation = v.coerceIn(0f, 2f))
                        "exposure" -> cur.copy(exposure = v.coerceIn(-1f, 1f))
                        "blur" -> cur.copy(blurRadius = v.coerceIn(0f, 25f))
                        "sharpen" -> cur.copy(sharpen = v.coerceIn(0f, 1f))
                        "opacity" -> cur.copy(opacity = v.coerceIn(0f, 1f))
                        "color_filter" -> {
                            val preset = op.color_filter?.let {
                                runCatching { ColorFilterPreset.valueOf(it.uppercase()) }.getOrNull()
                            } ?: ColorFilterPreset.CINEMA
                            cur.copy(colorFilter = preset)
                        }
                        else -> cur.copy(motionEffect = MotionEffectPreset.ZOOM_IN)
                    }
                    clip.copy(
                        effects = updated.copy(opacity = op.opacity?.coerceIn(0f, 1f) ?: updated.opacity),
                        scale = op.scale ?: clip.scale
                    )
                }!!
            }
            "set_keyframe" -> {
                val clip = currentDocument.clips.first { it.id == op.clip_id }
                val offset = (op.time_offset_ms ?: (playheadMs - clip.startMs)).coerceIn(0L, clip.durationMs)
                val kf = Keyframe(
                    id = UUID.randomUUID().toString(),
                    timeOffsetMs = offset,
                    x = op.x ?: clip.positionX,
                    y = op.y ?: clip.positionY,
                    scale = op.scale ?: clip.scale,
                    rotationDeg = op.rotation_deg ?: clip.rotationDeg,
                    opacity = op.opacity ?: clip.effects.opacity,
                    volume = op.volume ?: clip.volume
                )
                updateClip(clip.id, "Added keyframe at ${formatMs(offset)}") {
                    it.copy(keyframes = (it.keyframes + kf).sortedBy { k -> k.timeOffsetMs })
                }!!
            }
            else -> EngineExecutionResult(currentDocument, null, "No operation executed.")
        }
    }

    private fun opacityOrPreserve(newOpacity: Float?, clip: TimelineClip): Float =
        newOpacity?.coerceIn(0f, 1f) ?: clip.effects.opacity

    private fun TimelineClip.copy(
        effects: ClipEffects,
        scale: Float,
        opacityVal: Float
    ): TimelineClip = this.copy(
        effects = effects.copy(opacity = opacityVal),
        scale = scale
    )

    private fun snapIfEnabled(targetMs: Long, ignoreClipId: String?): Long {
        if (!currentDocument.snappingEnabled) return targetMs
        val thresholdMs = 350L
        val snapPoints = buildList {
            add(0L)
            currentDocument.clips.filter { it.id != ignoreClipId }.forEach {
                add(it.startMs)
                add(it.endMs)
            }
        }
        return snapPoints.minByOrNull { kotlin.math.abs(it - targetMs) }
            ?.takeIf { kotlin.math.abs(it - targetMs) <= thresholdMs }
            ?: targetMs
    }

    private fun parseHexColor(hex: String?): Long? {
        if (hex.isNullOrBlank()) return null
        val clean = hex.trim().removePrefix("#")
        return when (clean.length) {
            6 -> ("FF$clean").toLongOrNull(16)
            8 -> clean.toLongOrNull(16)
            else -> null
        }
    }

    companion object {
        fun formatMs(ms: Long): String {
            val totalSec = (ms / 1000L).coerceAtLeast(0L)
            val mins = totalSec / 60
            val secs = totalSec % 60
            val hrs = mins / 60
            val remMins = mins % 60
            return if (hrs > 0) {
                String.format("%02d:%02d:%02d", hrs, remMins, secs)
            } else {
                String.format("%02d:%02d", remMins, secs)
            }
        }
    }
}

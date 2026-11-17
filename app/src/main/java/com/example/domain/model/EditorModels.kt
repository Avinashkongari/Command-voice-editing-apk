package com.example.domain.model

import com.squareup.moshi.JsonClass

enum class AspectRatioPreset(val label: String, val widthRatio: Int, val heightRatio: Int) {
    RATIO_16_9("16:9 YouTube", 16, 9),
    RATIO_9_16("9:16 Reels", 9, 16),
    RATIO_1_1("1:1 Square", 1, 1),
    RATIO_4_5("4:5 Portrait", 4, 5);

    fun dimensionsForHeight(shortEdge: Int): Pair<Int, Int> {
        return when (this) {
            RATIO_16_9 -> (shortEdge * 16 / 9).makeEven() to shortEdge.makeEven()
            RATIO_9_16 -> shortEdge.makeEven() to (shortEdge * 16 / 9).makeEven()
            RATIO_1_1 -> shortEdge.makeEven() to shortEdge.makeEven()
            RATIO_4_5 -> shortEdge.makeEven() to (shortEdge * 5 / 4).makeEven()
        }
    }

    private fun Int.makeEven(): Int = if (this % 2 == 0) this else this + 1
}

enum class ExportResolution(val label: String, val shortEdgePx: Int) {
    RES_480P("480p SD", 480),
    RES_720P("720p HD", 720),
    RES_1080P("1080p Full HD", 1080),
    RES_2K("2K QHD", 1440),
    RES_4K("4K UHD", 2160)
}

enum class FrameRateOption(val fps: Int, val label: String) {
    FPS_24(24, "24 FPS"),
    FPS_30(30, "30 FPS"),
    FPS_60(60, "60 FPS")
}

enum class BitratePreset(val bps: Int, val label: String) {
    LOW(4_000_000, "4 Mbps (Compact)"),
    STANDARD(8_000_000, "8 Mbps (Balanced)"),
    HIGH(16_000_000, "16 Mbps (High)"),
    ULTRA(32_000_000, "32 Mbps (Master)")
}

enum class MediaAssetType {
    IMAGE,
    VIDEO,
    AUDIO,
    VOICEOVER
}

enum class TrackType(val id: String, val displayName: String) {
    VIDEO_MAIN("video_main", "Main Video"),
    IMAGE_OVERLAY("image_overlay", "Image / Overlay"),
    TEXT_SUBTITLE("text_track", "Text & Subtitles"),
    EFFECT_OVERLAY("effect_track", "FX & Filters"),
    AUDIO_MAIN("audio_main", "Audio / Story"),
    AUDIO_VOICEOVER("audio_voiceover", "Voiceover");

    companion object {
        fun fromId(raw: String?): TrackType {
            return entries.firstOrNull {
                it.id.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true)
            } ?: when (raw?.lowercase()) {
                "video", "main" -> VIDEO_MAIN
                "image", "overlay", "pip" -> IMAGE_OVERLAY
                "text", "subtitle", "subtitles", "captions" -> TEXT_SUBTITLE
                "effect", "effects", "filter" -> EFFECT_OVERLAY
                "audio", "music", "bgm" -> AUDIO_MAIN
                "voiceover", "voice", "narration" -> AUDIO_VOICEOVER
                else -> IMAGE_OVERLAY
            }
        }
    }
}

enum class TransitionType(val displayName: String) {
    NONE("None"),
    FADE("Fade"),
    CROSSFADE("Crossfade"),
    DISSOLVE("Dissolve"),
    SLIDE_LEFT("Slide Left"),
    SLIDE_RIGHT("Slide Right"),
    ZOOM_IN("Zoom In")
}

enum class ColorFilterPreset(val displayName: String) {
    NONE("Original"),
    CINEMA("Cinema"),
    WARM("Golden Warm"),
    COOL("Arctic Cool"),
    BW("Noir B&W"),
    VINTAGE("Vintage Film"),
    VIVID("Vivid Pop")
}

enum class MotionEffectPreset(val displayName: String) {
    NONE("Static"),
    ZOOM_IN("Slow Zoom In"),
    ZOOM_OUT("Slow Zoom Out"),
    PAN_LEFT("Pan Left"),
    PAN_RIGHT("Pan Right"),
    KEN_BURNS("Ken Burns")
}

enum class TextAnimationPreset(val displayName: String) {
    NONE("None"),
    FADE("Fade In"),
    SLIDE_UP("Slide Up"),
    POP("Pop Scale"),
    TYPEWRITER("Typewriter")
}

@JsonClass(generateAdapter = true)
data class Keyframe(
    val id: String,
    val timeOffsetMs: Long,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val scale: Float = 1f,
    val stretchX: Float = 1f,
    val stretchY: Float = 1f,
    val backgroundBlurRadius: Float = 0f,
    val rotationDeg: Float = 0f,
    val opacity: Float = 1f,
    val volume: Float = 1f
)

@JsonClass(generateAdapter = true)
data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
)

@JsonClass(generateAdapter = true)
data class ClipEffects(
    val brightness: Float = 0f,       // -1f to 1f
    val contrast: Float = 0f,         // -1f to 1f
    val saturation: Float = 1f,       // 0f to 2f
    val exposure: Float = 0f,         // -1f to 1f
    val blurRadius: Float = 0f,       // 0f to 25f
    val sharpen: Float = 0f,          // 0f to 1f
    val opacity: Float = 1f,          // 0f to 1f
    val colorFilter: ColorFilterPreset = ColorFilterPreset.NONE,
    val motionEffect: MotionEffectPreset = MotionEffectPreset.NONE
)

@JsonClass(generateAdapter = true)
data class ClipTextStyle(
    val text: String = "",
    val isSubtitle: Boolean = false,
    val fontName: String = "Space Grotesk",
    val fontSizeSp: Float = 26f,
    val textColorHex: Long = 0xFFFFFFFF,
    val backgroundColorHex: Long = 0x80000000,
    val positionX: Float = 0.5f,
    val positionY: Float = 0.82f,
    val animation: TextAnimationPreset = TextAnimationPreset.FADE
)

@JsonClass(generateAdapter = true)
data class TimelineClip(
    val id: String,
    val trackType: TrackType,
    val assetId: String? = null,
    val label: String,
    val startMs: Long,
    val endMs: Long,
    val sourceStartMs: Long = 0L,
    val sourceEndMs: Long = 0L,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val isMuted: Boolean = false,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val positionX: Float = 0.5f,
    val positionY: Float = 0.5f,
    val scale: Float = 1f,
    val stretchX: Float = 1f,
    val stretchY: Float = 1f,
    val rotationDeg: Float = 0f,
    val crop: CropRect = CropRect(),
    val transitionIn: TransitionType = TransitionType.NONE,
    val transitionInDurationMs: Long = 500L,
    val transitionOut: TransitionType = TransitionType.NONE,
    val transitionOutDurationMs: Long = 500L,
    val effects: ClipEffects = ClipEffects(),
    val textStyle: ClipTextStyle? = null,
    val keyframes: List<Keyframe> = emptyList()
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(100L)
}

@JsonClass(generateAdapter = true)
data class MediaAsset(
    val id: String,
    val projectId: String,
    val uriString: String,
    val displayName: String,
    val type: MediaAssetType,
    val mimeType: String = "",
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val fileSizeBytes: Long = 0L,
    val tags: String = "",
    val waveformPeaks: List<Float> = emptyList(),
    val thumbnailPath: String? = null,
    val importedAtMs: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class TimelineDocument(
    val clips: List<TimelineClip> = emptyList(),
    val defaultImageDurationMs: Long = 5000L,
    val defaultTransitionDurationMs: Long = 500L,
    val snappingEnabled: Boolean = true
) {
    val totalDurationMs: Long
        get() = clips.maxOfOrNull { it.endMs }?.coerceAtLeast(10_000L) ?: 10_000L
}

data class VideoProject(
    val id: String,
    val title: String,
    val aspectRatio: AspectRatioPreset = AspectRatioPreset.RATIO_16_9,
    val targetResolution: ExportResolution = ExportResolution.RES_1080P,
    val targetFps: FrameRateOption = FrameRateOption.FPS_30,
    val autosaveEnabled: Boolean = true,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
    val timeline: TimelineDocument = TimelineDocument()
)

data class EditHistoryRecord(
    val id: String,
    val description: String,
    val timestampMs: Long,
    val canUndo: Boolean = true
)

enum class AiProviderType(val displayName: String, val defaultBaseUrl: String) {
    GEMINI("Google Gemini API", "https://generativelanguage.googleapis.com/v1beta"),
    OPENAI_COMPATIBLE("OpenAI-Compatible API", "https://api.openai.com/v1"),
    CUSTOM("Custom REST Endpoint", "https://api.example.com/v1")
}

@JsonClass(generateAdapter = true)
data class AiModelInfo(
    val id: String,
    val displayName: String,
    val capabilities: String = ""
)

data class AiProviderProfile(
    val id: String,
    val providerName: String,
    val providerType: AiProviderType,
    val baseUrl: String,
    val selectedModelId: String,
    val customChatPath: String = "/chat/completions",
    val customAuthHeader: String = "Authorization",
    val availableModels: List<AiModelInfo> = emptyList(),
    val isConnectionTested: Boolean = false,
    val lastTestMessage: String = "Not tested",
    val lastTestedAtMs: Long = 0L,
    val isActive: Boolean = false,
    val hasStoredApiKey: Boolean = false
)

enum class ChatRole {
    USER,
    ASSISTANT,
    SYSTEM_STATUS,
    ERROR
}

data class AiChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val operationSummary: String? = null,
    val rawCommandJson: String? = null,
    val missingAssetType: MediaAssetType? = null,
    val missingAssetPrompt: String? = null,
    val pendingUserCommand: String? = null,
    val isVoiceInput: Boolean = false
)

@JsonClass(generateAdapter = true)
data class AiEditorOperation(
    val operation: String,
    val asset_id: String? = null,
    val asset_query: String? = null,
    val clip_id: String? = null,
    val track: String? = null,
    val start_ms: Long? = null,
    val end_ms: Long? = null,
    val duration_ms: Long? = null,
    val split_ms: Long? = null,
    val trim_start_ms: Long? = null,
    val trim_end_ms: Long? = null,
    val speed: Float? = null,
    val volume: Float? = null,
    val fade_in_ms: Long? = null,
    val fade_out_ms: Long? = null,
    val rotation_deg: Float? = null,
    val scale: Float? = null,
    val stretch_x: Float? = null,
    val stretch_y: Float? = null,
    val background_blur_radius: Float? = null,
    val x: Float? = null,
    val y: Float? = null,
    val opacity: Float? = null,
    val crop_left: Float? = null,
    val crop_top: Float? = null,
    val crop_right: Float? = null,
    val crop_bottom: Float? = null,
    val effect: String? = null,
    val value: Float? = null,
    val color_filter: String? = null,
    val transition: String? = null,
    val text: String? = null,
    val is_subtitle: Boolean? = null,
    val font_size: Float? = null,
    val text_color: String? = null,
    val text_animation: String? = null,
    val time_offset_ms: Long? = null,
    val missing_asset_type: String? = null,
    val missing_asset_hint: String? = null,
    val question: String? = null
)

@JsonClass(generateAdapter = true)
data class AiCommandEnvelope(
    val reply: String = "",
    val status_preview: String = "",
    val operations: List<AiEditorOperation> = emptyList()
)

data class ExportSettings(
    val resolution: ExportResolution = ExportResolution.RES_1080P,
    val frameRate: FrameRateOption = FrameRateOption.FPS_30,
    val bitrate: BitratePreset = BitratePreset.STANDARD,
    val fileName: String = "VoxCut_Export"
)

sealed interface ExportProgressState {
    data object Idle : ExportProgressState
    data class Running(val percent: Int, val stage: String) : ExportProgressState
    data class Completed(
        val outputFilePath: String,
        val contentUriString: String,
        val fileSizeBytes: Long,
        val durationMs: Long
    ) : ExportProgressState
    data class Failed(val errorMessage: String) : ExportProgressState
}

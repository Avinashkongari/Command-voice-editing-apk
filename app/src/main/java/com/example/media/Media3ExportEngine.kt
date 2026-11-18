package com.example.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SpeedChangingAudioProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.Crop
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.GaussianBlurWithFrameOverlaid
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.example.domain.model.ColorFilterPreset
import com.example.domain.model.ExportProgressState
import com.example.domain.model.ExportResolution
import com.example.domain.model.ExportSettings
import com.example.domain.model.MediaAsset
import com.example.domain.model.MotionEffectPreset
import com.example.domain.model.TimelineClip
import com.example.domain.model.TrackType
import com.example.domain.model.VideoProject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import kotlin.math.max
import kotlin.math.min

data class DeviceEncoderCapabilities(
    val maxSupportedWidth: Int,
    val maxSupportedHeight: Int,
    val supports4k: Boolean,
    val supports2k: Boolean,
    val supports60Fps: Boolean,
    val availableStorageBytes: Long
)

@OptIn(UnstableApi::class)
class Media3ExportEngine(
    private val context: Context,
    private val metadataHelper: MediaMetadataHelper
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeTransformer: Transformer? = null
    private var progressRunnable: Runnable? = null

    private val _exportState = MutableStateFlow<ExportProgressState>(ExportProgressState.Idle)
    val exportState: StateFlow<ExportProgressState> = _exportState.asStateFlow()

    fun queryDeviceCapabilities(): DeviceEncoderCapabilities {
        var maxW = 0
        var maxH = 0
        var supports60 = false
        var supports2k = false
        var supports4k = false
        runCatching {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (info in codecList.codecInfos) {
                if (!info.isEncoder) continue
                val avcType = info.supportedTypes.firstOrNull { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) }
                    ?: continue
                val caps = info.getCapabilitiesForType(avcType).videoCapabilities ?: continue
                maxW = max(maxW, caps.supportedWidths.upper)
                maxH = max(maxH, caps.supportedHeights.upper)
                supports60 = supports60 || runCatching {
                    caps.areSizeAndRateSupported(1920, 1080, 60.0) ||
                        caps.areSizeAndRateSupported(1080, 1920, 60.0)
                }.getOrDefault(false)
                supports2k = supports2k || runCatching {
                    caps.areSizeAndRateSupported(2560, 1440, 30.0) ||
                        caps.areSizeAndRateSupported(1440, 2560, 30.0)
                }.getOrDefault(false)
                supports4k = supports4k || runCatching {
                    caps.areSizeAndRateSupported(3840, 2160, 30.0) ||
                        caps.areSizeAndRateSupported(2160, 3840, 30.0)
                }.getOrDefault(false)
            }
        }
        if (maxW == 0 || maxH == 0) {
            maxW = 1920
            maxH = 1080
        }
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        val stat = StatFs(storageDir.absolutePath)
        val availBytes = stat.availableBytes

        return DeviceEncoderCapabilities(
            maxSupportedWidth = maxW,
            maxSupportedHeight = maxH,
            supports4k = maxW >= 3840 || maxH >= 2160,
            supports2k = maxW >= 2560 || maxH >= 1440,
            supports60Fps = supports60,
            availableStorageBytes = availBytes
        )
    }

    fun startExport(
        project: VideoProject,
        assets: List<MediaAsset>,
        settings: ExportSettings
    ) {
        cancelExport()

        val timeline = project.timeline
        if (timeline.clips.isEmpty()) {
            _exportState.value = ExportProgressState.Failed("Timeline is empty. Add at least one video, image, or audio clip.")
            return
        }

        val caps = queryDeviceCapabilities()
        if (caps.availableStorageBytes < 40L * 1024L * 1024L) {
            _exportState.value = ExportProgressState.Failed("Insufficient device storage (< 40 MB free). Free up space before exporting.")
            return
        }

        if (settings.resolution == ExportResolution.RES_4K && !caps.supports4k) {
            _exportState.value = ExportProgressState.Failed("This device's hardware H.264 encoder does not support 4K resolution. Select 1080p or 2K.")
            return
        }
        if (settings.resolution == ExportResolution.RES_2K && !caps.supports2k) {
            _exportState.value = ExportProgressState.Failed("This device's hardware H.264 encoder does not support 2K at 30 FPS. Select 1080p.")
            return
        }
        if (settings.frameRate == com.example.domain.model.FrameRateOption.FPS_60 && !caps.supports60Fps) {
            _exportState.value = ExportProgressState.Failed("This device's hardware H.264 encoder does not support 1080p at 60 FPS. Select 30 FPS.")
            return
        }

        _exportState.value = ExportProgressState.Running(2, "Preparing Media3 Composition...")

        val moviesDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir, "VoxCutStudio")
            .apply { mkdirs() }
        val safeName = settings.fileName.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "VoxCut_Export" }
        val tempOutputFile = File(moviesDir, "${safeName}_${System.currentTimeMillis()}.mp4")

        val assetMap = assets.associateBy { it.id }
        val (outWidth, outHeight) = project.aspectRatio.dimensionsForHeight(settings.resolution.shortEdgePx)
        val fps = settings.frameRate.fps
        val totalDurationMs = timeline.totalDurationMs.coerceAtLeast(1000L)

        try {
            val composition = buildComposition(
                clips = timeline.clips,
                assetMap = assetMap,
                totalDurationMs = totalDurationMs,
                outWidth = outWidth,
                outHeight = outHeight,
                fps = fps
            )

            // Honor the bitrate selected in Export Settings instead of silently using
            // Media3's automatic default. VBR keeps quality efficient for mixed scenes.
            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(
                    VideoEncoderSettings.Builder()
                        .setBitrate(settings.bitrate.bps)
                        .build()
                )
                .build()
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoderFactory)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        stopProgressPolling()
                        activeTransformer = null
                        if (!tempOutputFile.exists() || tempOutputFile.length() <= 0L) {
                            _exportState.value = ExportProgressState.Failed("Export finished but the MP4 output is missing or empty. Please try again.")
                            return
                        }
                        val publishedUri = runCatching {
                            publishToMediaStore(tempOutputFile, "${safeName}.mp4")
                        }.getOrNull()
                        _exportState.value = ExportProgressState.Completed(
                            outputFilePath = tempOutputFile.absolutePath,
                            contentUriString = publishedUri?.toString() ?: Uri.fromFile(tempOutputFile).toString(),
                            fileSizeBytes = tempOutputFile.length(),
                            durationMs = exportResult.durationMs.takeIf { it > 0 } ?: totalDurationMs
                        )
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        stopProgressPolling()
                        activeTransformer = null
                        tempOutputFile.delete()
                        val reason = exportException.message ?: "Media3 encoding failed (${exportException.errorCodeName})."
                        _exportState.value = ExportProgressState.Failed(reason)
                    }
                })
                .build()

            activeTransformer = transformer
            _exportState.value = ExportProgressState.Running(5, "Encoding ${settings.resolution.label} @ ${fps}fps • ${settings.bitrate.label}...")
            transformer.start(composition, tempOutputFile.absolutePath)
            startProgressPolling(transformer)
        } catch (e: Exception) {
            tempOutputFile.delete()
            _exportState.value = ExportProgressState.Failed("Export initialization error: ${e.message ?: "Unsupported media format"}")
        }
    }

    fun cancelExport() {
        stopProgressPolling()
        runCatching { activeTransformer?.cancel() }
        activeTransformer = null
        if (_exportState.value is ExportProgressState.Running) {
            _exportState.value = ExportProgressState.Idle
        }
    }

    fun resetState() {
        _exportState.value = ExportProgressState.Idle
    }

    private fun startProgressPolling(transformer: Transformer) {
        val holder = ProgressHolder()
        val runnable = object : Runnable {
            override fun run() {
                val state = transformer.getProgress(holder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    val pct = holder.progress.coerceIn(5, 99)
                    _exportState.value = ExportProgressState.Running(pct, "Rendering MP4 frames & audio ($pct%)...")
                }
                if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                    mainHandler.postDelayed(this, 250L)
                }
            }
        }
        progressRunnable = runnable
        mainHandler.postDelayed(runnable, 250L)
    }

    private fun stopProgressPolling() {
        progressRunnable?.let { mainHandler.removeCallbacks(it) }
        progressRunnable = null
    }

    private fun buildComposition(
        clips: List<TimelineClip>,
        assetMap: Map<String, MediaAsset>,
        totalDurationMs: Long,
        outWidth: Int,
        outHeight: Int,
        fps: Int
    ): Composition {
        val blackCanvasUri = Uri.fromFile(metadataHelper.ensureBlackFrameImageFile())

        // Primary visual items combine VIDEO_MAIN and IMAGE_OVERLAY clips sorted into non-overlapping segments;
        // simultaneous text/subtitle/image overlays are rendered via BitmapOverlay onto the base segment!
        val baseClips = clips
            .filter { it.trackType == TrackType.VIDEO_MAIN || it.trackType == TrackType.IMAGE_OVERLAY }
            .sortedBy { it.startMs }

        val textAndSubClips = clips.filter { it.trackType == TrackType.TEXT_SUBTITLE && it.textStyle != null }
        val overlayImageClips = clips.filter { it.trackType == TrackType.IMAGE_OVERLAY }

        val visualSequenceItems = mutableListOf<EditedMediaItem>()
        var cursorMs = 0L

        // Build flattened visual timeline so exact timestamps (e.g., 00:01:20 -> 00:01:38) stay synced with audio!
        val primaryTrackClips = if (baseClips.any { it.trackType == TrackType.VIDEO_MAIN }) {
            baseClips.filter { it.trackType == TrackType.VIDEO_MAIN }
        } else {
            baseClips
        }

        for (clip in primaryTrackClips) {
            val asset = clip.assetId?.let { assetMap[it] } ?: continue
            if (clip.startMs > cursorMs) {
                val gapMs = clip.startMs - cursorMs
                val gapOverlays = collectOverlayEffectForSpan(
                    spanStartMs = cursorMs,
                    spanEndMs = clip.startMs,
                    textClips = textAndSubClips,
                    overlayImages = overlayImageClips,
                    assetMap = assetMap,
                    outWidth = outWidth,
                    outHeight = outHeight
                )
                visualSequenceItems.add(buildGapItem(blackCanvasUri, gapMs, fps, gapOverlays))
                cursorMs = clip.startMs
            }

            val clippedStart = max(clip.startMs, cursorMs)
            if (clip.endMs <= clippedStart) continue
            val clipDur = clip.endMs - clippedStart

            val spanOverlays = collectOverlayEffectForSpan(
                spanStartMs = clippedStart,
                spanEndMs = clip.endMs,
                textClips = textAndSubClips,
                overlayImages = if (clip.trackType == TrackType.IMAGE_OVERLAY) emptyList() else overlayImageClips,
                assetMap = assetMap,
                outWidth = outWidth,
                outHeight = outHeight
            )

            visualSequenceItems.add(
                buildVisualEditedItem(
                    clip = clip.copy(
                        startMs = clippedStart,
                        sourceStartMs = if (asset.type == com.example.domain.model.MediaAssetType.IMAGE) {
                            clip.sourceStartMs
                        } else {
                            clip.sourceStartMs + ((clippedStart - clip.startMs).coerceAtLeast(0L) * clip.speed).toLong()
                        }
                    ),
                    asset = asset,
                    targetDurationMs = clipDur,
                    fps = fps,
                    extraOverlays = spanOverlays
                )
            )
            cursorMs = clip.endMs
        }

        if (cursorMs < totalDurationMs) {
            val tailMs = totalDurationMs - cursorMs
            val tailOverlays = collectOverlayEffectForSpan(
                spanStartMs = cursorMs,
                spanEndMs = totalDurationMs,
                textClips = textAndSubClips,
                overlayImages = overlayImageClips,
                assetMap = assetMap,
                outWidth = outWidth,
                outHeight = outHeight
            )
            visualSequenceItems.add(buildGapItem(blackCanvasUri, tailMs, fps, tailOverlays))
        }

        if (visualSequenceItems.isEmpty()) {
            visualSequenceItems.add(buildGapItem(blackCanvasUri, totalDurationMs, fps, emptyList()))
        }

        val sequences = mutableListOf<EditedMediaItemSequence>()
        sequences.add(
            EditedMediaItemSequence.Builder(visualSequenceItems).build()
        )

        // Audio tracks (story narration, BGM, voiceover)
        val audioClips = clips
            .filter { (it.trackType == TrackType.AUDIO_MAIN || it.trackType == TrackType.AUDIO_VOICEOVER) && !it.isMuted }
            .sortedBy { it.startMs }

        audioClips.forEach { audioClip ->
            val asset = audioClip.assetId?.let { assetMap[it] } ?: return@forEach
            val audioMediaItem = MediaItem.Builder()
                .setUri(Uri.parse(asset.uriString))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(audioClip.sourceStartMs.coerceAtLeast(0L))
                        .setEndPositionMs(
                            (audioClip.sourceStartMs + audioClip.durationMs)
                                .coerceAtLeast(audioClip.sourceStartMs + 500L)
                        )
                        .build()
                )
                .build()

            val audioProcessors = buildAudioProcessors(audioClip)
            val editedAudio = EditedMediaItem.Builder(audioMediaItem)
                .setRemoveVideo(true)
                .setEffects(Effects(audioProcessors, emptyList()))
                .build()

            sequences.add(
                EditedMediaItemSequence.Builder(listOf(editedAudio)).build()
            )
        }

        val presentation = Presentation.createForWidthAndHeight(
            outWidth,
            outHeight,
            Presentation.LAYOUT_SCALE_TO_FIT
        )

        return Composition.Builder(sequences)
            .setEffects(Effects(emptyList(), listOf(presentation)))
            .build()
    }

    private fun buildGapItem(
        blackUri: Uri,
        durationMs: Long,
        fps: Int,
        overlays: List<Effect>
    ): EditedMediaItem {
        val mediaItem = MediaItem.Builder()
            .setUri(blackUri)
            .setImageDurationMs(durationMs.coerceAtLeast(100L))
            .build()
        return EditedMediaItem.Builder(mediaItem)
            .setFrameRate(fps)
            .setEffects(Effects(emptyList(), overlays))
            .build()
    }

    private fun buildVisualEditedItem(
        clip: TimelineClip,
        asset: MediaAsset,
        targetDurationMs: Long,
        fps: Int,
        extraOverlays: List<Effect>
    ): EditedMediaItem {
        val isImage = asset.type == com.example.domain.model.MediaAssetType.IMAGE
        val builder = MediaItem.Builder().setUri(Uri.parse(asset.uriString))
        if (isImage) {
            builder.setImageDurationMs(targetDurationMs.coerceAtLeast(250L))
        } else {
            val endSrc = if (clip.sourceEndMs > clip.sourceStartMs) {
                clip.sourceEndMs
            } else {
                clip.sourceStartMs + (targetDurationMs * clip.speed).toLong()
            }
            builder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.sourceStartMs.coerceAtLeast(0L))
                    .setEndPositionMs(endSrc.coerceAtLeast(clip.sourceStartMs + 250L))
                    .build()
            )
        }

        val videoEffects = buildVideoEffectsForClip(clip, targetDurationMs) + extraOverlays
        val audioProcessors = if (isImage || clip.isMuted) emptyList() else buildAudioProcessors(clip)

        return EditedMediaItem.Builder(builder.build())
            .setFrameRate(fps)
            .setRemoveAudio(isImage || clip.isMuted)
            .setEffects(Effects(audioProcessors, videoEffects))
            .build()
    }

    private fun buildVideoEffectsForClip(clip: TimelineClip, durationMs: Long): List<Effect> {
        val list = mutableListOf<Effect>()

        // Crop
        val c = clip.crop
        if (c.left > 0.01f || c.top > 0.01f || c.right < 0.99f || c.bottom < 0.99f) {
            list.add(
                Crop(
                    c.left * 2f - 1f,
                    c.right * 2f - 1f,
                    1f - c.bottom * 2f,
                    1f - c.top * 2f
                )
            )
        }

        // Static base scale & rotation.
        if (clip.scale != 1f || clip.stretchX != 1f || clip.stretchY != 1f || clip.rotationDeg != 0f) {
            list.add(
                ScaleAndRotateTransformation.Builder()
                    .setScale(clip.scale * clip.stretchX, clip.scale * clip.stretchY)
                    .setRotationDegrees(-clip.rotationDeg)
                    .build()
            )
        }

        // Keyframe transform animation: scale, independent X/Y stretch, rotation and
        // position use the same interpolated values as the live preview.
        if (clip.keyframes.size >= 2) {
            val frames = clip.keyframes.sortedBy { it.timeOffsetMs }
            list.add(MatrixTransformation { timeUs ->
                val localMs = (timeUs / 1000L).coerceIn(0L, durationMs)
                val before = frames.lastOrNull { it.timeOffsetMs <= localMs }
                val after = frames.firstOrNull { it.timeOffsetMs > localMs }
                val fraction = if (before != null && after != null && after.timeOffsetMs > before.timeOffsetMs) {
                    ((localMs - before.timeOffsetMs).toFloat() /
                        (after.timeOffsetMs - before.timeOffsetMs).toFloat()).coerceIn(0f, 1f)
                } else 0f
                val first = frames.first()
                val left = before ?: first
                val right = after ?: left
                fun mix(a: Float, b: Float): Float = a + (b - a) * fraction

                val animatedScale = mix(left.scale, right.scale)
                val animatedStretchX = mix(left.stretchX, right.stretchX)
                val animatedStretchY = mix(left.stretchY, right.stretchY)
                val animatedRotation = mix(left.rotationDeg, right.rotationDeg)
                val animatedX = mix(left.x, right.x)
                val animatedY = mix(left.y, right.y)

                val factorX = ((animatedScale * animatedStretchX) /
                    (clip.scale * clip.stretchX).coerceAtLeast(0.001f)).coerceIn(0.1f, 6f)
                val factorY = ((animatedScale * animatedStretchY) /
                    (clip.scale * clip.stretchY).coerceAtLeast(0.001f)).coerceIn(0.1f, 6f)
                val rotationDelta = animatedRotation - clip.rotationDeg
                val translateX = (animatedX - clip.positionX) * 2f
                val translateY = (animatedY - clip.positionY) * 2f

                Matrix().apply {
                    postScale(factorX, factorY)
                    postRotate(rotationDelta)
                    postTranslate(translateX, translateY)
                }
            })
        }

        // Motion effects (Ken Burns / Zoom In / Zoom Out / Pan)
        val durUs = (durationMs * 1000f).coerceAtLeast(500_000f)
        when (clip.effects.motionEffect) {
            MotionEffectPreset.ZOOM_IN -> {
                list.add(MatrixTransformation { timeUs ->
                    val progress = (timeUs / durUs).coerceIn(0f, 1f)
                    val s = 1f + 0.18f * progress
                    Matrix().apply { postScale(s, s) }
                })
            }
            MotionEffectPreset.ZOOM_OUT -> {
                list.add(MatrixTransformation { timeUs ->
                    val progress = (timeUs / durUs).coerceIn(0f, 1f)
                    val s = 1.18f - 0.18f * progress
                    Matrix().apply { postScale(s, s) }
                })
            }
            MotionEffectPreset.PAN_LEFT -> {
                list.add(MatrixTransformation { timeUs ->
                    val progress = (timeUs / durUs).coerceIn(0f, 1f)
                    Matrix().apply {
                        postScale(1.12f, 1.12f)
                        postTranslate(-0.08f * progress, 0f)
                    }
                })
            }
            MotionEffectPreset.PAN_RIGHT -> {
                list.add(MatrixTransformation { timeUs ->
                    val progress = (timeUs / durUs).coerceIn(0f, 1f)
                    Matrix().apply {
                        postScale(1.12f, 1.12f)
                        postTranslate(0.08f * progress, 0f)
                    }
                })
            }
            MotionEffectPreset.KEN_BURNS -> {
                list.add(MatrixTransformation { timeUs ->
                    val progress = (timeUs / durUs).coerceIn(0f, 1f)
                    val s = 1.0f + 0.22f * progress
                    Matrix().apply {
                        postScale(s, s)
                        postTranslate(0.04f * progress, -0.03f * progress)
                    }
                })
            }
            MotionEffectPreset.NONE -> Unit
        }

        // Color & Exposure adjustments
        val e = clip.effects
        val effectiveBrightness = (e.brightness + e.exposure * 0.6f).coerceIn(-1f, 1f)
        if (kotlin.math.abs(effectiveBrightness) > 0.02f) {
            list.add(Brightness(effectiveBrightness))
        }
        if (kotlin.math.abs(e.contrast) > 0.02f) {
            list.add(Contrast(e.contrast.coerceIn(-1f, 1f)))
        }
        if (kotlin.math.abs(e.saturation - 1f) > 0.03f) {
            val hslSat = ((e.saturation - 1f) * 100f).coerceIn(-100f, 100f)
            list.add(HslAdjustment.Builder().adjustSaturation(hslSat).build())
        }

        when (e.colorFilter) {
            ColorFilterPreset.BW -> list.add(RgbFilter.createGrayscaleFilter())
            ColorFilterPreset.WARM -> list.add(RgbAdjustment.Builder().setRedScale(1.15f).setBlueScale(0.88f).build())
            ColorFilterPreset.COOL -> list.add(RgbAdjustment.Builder().setBlueScale(1.18f).setRedScale(0.88f).build())
            ColorFilterPreset.CINEMA -> {
                list.add(Contrast(0.18f))
                list.add(RgbAdjustment.Builder().setGreenScale(1.04f).setBlueScale(1.06f).build())
            }
            ColorFilterPreset.VINTAGE -> {
                list.add(RgbAdjustment.Builder().setRedScale(1.12f).setGreenScale(1.04f).setBlueScale(0.78f).build())
            }
            ColorFilterPreset.VIVID -> {
                list.add(HslAdjustment.Builder().adjustSaturation(30f).build())
            }
            ColorFilterPreset.NONE -> Unit
        }

        // Speed change for video clips
        if (kotlin.math.abs(clip.speed - 1f) > 0.05f) {
            list.add(SpeedChangeEffect(object : SpeedProvider {
                override fun getSpeed(timeUs: Long): Float = clip.speed
                override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = androidx.media3.common.C.TIME_UNSET
            }))
        }

        if (clip.backgroundBlurRadius > 0.1f) {
            list.add(GaussianBlurWithFrameOverlaid(clip.backgroundBlurRadius.coerceIn(1f, 40f), 1f, 1f))
        }

        return list
    }

    private fun buildAudioProcessors(clip: TimelineClip): List<AudioProcessor> {
        val list = mutableListOf<AudioProcessor>()
        if (kotlin.math.abs(clip.volume - 1f) > 0.02f) {
            val gain = clip.volume.coerceIn(0f, 2f)
            val processor = ChannelMixingAudioProcessor()
            processor.putChannelMixingMatrix(
                ChannelMixingMatrix.createForConstantGain(2, 2).scaleBy(gain)
            )
            processor.putChannelMixingMatrix(
                ChannelMixingMatrix.createForConstantGain(1, 1).scaleBy(gain)
            )
            list.add(processor)
        }
        if (kotlin.math.abs(clip.speed - 1f) > 0.05f) {
            list.add(SpeedChangingAudioProcessor(object : SpeedProvider {
                override fun getSpeed(timeUs: Long): Float = clip.speed
                override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = androidx.media3.common.C.TIME_UNSET
            }))
        }
        return list
    }

    private fun collectOverlayEffectForSpan(
        spanStartMs: Long,
        spanEndMs: Long,
        textClips: List<TimelineClip>,
        overlayImages: List<TimelineClip>,
        assetMap: Map<String, MediaAsset>,
        outWidth: Int,
        outHeight: Int
    ): List<Effect> {
        val activeTexts = textClips.filter { it.startMs < spanEndMs && it.endMs > spanStartMs }
        val activeImages = overlayImages.filter { it.startMs < spanEndMs && it.endMs > spanStartMs }
        if (activeTexts.isEmpty() && activeImages.isEmpty()) return emptyList()

        val overlayBitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(overlayBitmap)

        // Draw overlay images
        activeImages.forEach { imgClip ->
            val asset = imgClip.assetId?.let { assetMap[it] } ?: return@forEach
            val bmp = decodeScaledBitmap(Uri.parse(asset.uriString), outWidth, outHeight) ?: return@forEach
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                alpha = (imgClip.effects.opacity * 255).toInt().coerceIn(0, 255)
            }
            val cx = outWidth * imgClip.positionX
            val cy = outHeight * imgClip.positionY
            val fitScale = min(outWidth.toFloat() / bmp.width, outHeight.toFloat() / bmp.height) * imgClip.scale
            val matrix = Matrix().apply {
                postTranslate(-bmp.width / 2f, -bmp.height / 2f)
                postScale(fitScale, fitScale)
                postRotate(imgClip.rotationDeg)
                postTranslate(cx, cy)
            }
            canvas.drawBitmap(bmp, matrix, paint)
            bmp.recycle()
        }

        // Draw texts and subtitles
        activeTexts.forEach { txtClip ->
            val style = txtClip.textStyle ?: return@forEach
            if (style.text.isBlank()) return@forEach
            val scaleFactor = outHeight / 720f
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.textColorHex.toInt()
                textSize = style.fontSizeSp * scaleFactor * 1.4f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                setShadowLayer(4f * scaleFactor, 0f, 2f * scaleFactor, android.graphics.Color.BLACK)
            }
            val maxTextWidth = (outWidth * 0.86f).toInt().coerceAtLeast(100)
            val layout = StaticLayout.Builder
                .obtain(style.text, 0, style.text.length, textPaint, maxTextWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build()

            val drawX = (outWidth * style.positionX) - (layout.width / 2f)
            val drawY = (outHeight * style.positionY) - (layout.height / 2f)

            if (style.isSubtitle) {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = style.backgroundColorHex.toInt()
                }
                val pad = 16f * scaleFactor
                canvas.drawRoundRect(
                    drawX - pad,
                    drawY - pad * 0.5f,
                    drawX + layout.width + pad,
                    drawY + layout.height + pad * 0.5f,
                    12f * scaleFactor,
                    12f * scaleFactor,
                    bgPaint
                )
            }

            canvas.save()
            canvas.translate(drawX, drawY)
            layout.draw(canvas)
            canvas.restore()
        }

        return listOf(OverlayEffect(listOf(BitmapOverlay.createStaticBitmapOverlay(overlayBitmap))))
    }

    private fun decodeScaledBitmap(uri: Uri, maxW: Int, maxH: Int): Bitmap? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            while (bounds.outWidth / sample > maxW || bounds.outHeight / sample > maxH) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        }.getOrNull()
    }

    private fun publishToMediaStore(sourceFile: File, displayName: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return Uri.fromFile(sourceFile)
        }
        return runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/VoxCutStudio")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            try {
                val output = resolver.openOutputStream(uri, "w")
                    ?: throw IllegalStateException("Could not open the destination video stream.")
                output.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out, 128 * 1024)
                    }
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } catch (error: Exception) {
                resolver.delete(uri, null, null)
                throw error
            }
        }.getOrNull()
    }
}

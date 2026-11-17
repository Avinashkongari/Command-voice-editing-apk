package com.example.ui.components

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.ClipEffects
import com.example.domain.model.ColorFilterPreset
import com.example.domain.model.Keyframe
import com.example.domain.model.MediaAsset
import com.example.domain.model.MediaAssetType
import com.example.domain.model.MotionEffectPreset
import com.example.domain.model.TimelineClip
import com.example.domain.model.TimelineDocument
import com.example.domain.model.TrackType
import com.example.domain.model.TransitionType
import com.example.engine.TimelineEngine
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldAudio
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun LivePreviewStage(
    aspectRatio: AspectRatioPreset,
    timeline: TimelineDocument,
    assets: List<MediaAsset>,
    playheadMs: Long,
    isPlaying: Boolean,
    videoPlayer: ExoPlayer,
    modifier: Modifier = Modifier
) {
    val assetMap = remember(assets) { assets.associateBy { it.id } }

    val activeVideoClip = remember(timeline, playheadMs) {
        timeline.clips.firstOrNull {
            it.trackType == TrackType.VIDEO_MAIN && playheadMs in it.startMs until it.endMs
        }
    }
    val activeImageClips = remember(timeline, playheadMs) {
        timeline.clips.filter {
            it.trackType == TrackType.IMAGE_OVERLAY && playheadMs in it.startMs until it.endMs
        }
    }
    val activeTextClips = remember(timeline, playheadMs) {
        timeline.clips.filter {
            it.trackType == TrackType.TEXT_SUBTITLE && playheadMs in it.startMs until it.endMs
        }
    }
    val activeAudioClip = remember(timeline, playheadMs) {
        timeline.clips.firstOrNull {
            (it.trackType == TrackType.AUDIO_MAIN || it.trackType == TrackType.AUDIO_VOICEOVER) &&
                playheadMs in it.startMs until it.endMs
        }
    }

    val targetRatio = aspectRatio.widthRatio.toFloat() / aspectRatio.heightRatio.toFloat()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(StudioVoid),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            val containerRatio = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 16f / 9f
            val stageModifier = if (containerRatio > targetRatio) {
                Modifier
                    .height(maxHeight)
                    .aspectRatio(targetRatio)
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(targetRatio)
            }

            Box(
                modifier = stageModifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
                    .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
                    .clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                // 1. Base Video Track Layer
                if (activeVideoClip != null) {
                    val videoAsset = activeVideoClip.assetId?.let { assetMap[it] }
                    if (videoAsset?.type == MediaAssetType.VIDEO) {
                        val tf = evaluateTransform(activeVideoClip, playheadMs)
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = false
                                    player = videoPlayer
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                }
                            },
                            update = { view ->
                                if (view.player !== videoPlayer) view.player = videoPlayer
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = tf.scale
                                    scaleY = tf.scale
                                    rotationZ = tf.rotationDeg
                                    alpha = tf.opacity
                                    translationX = tf.translateXPx * size.width
                                    translationY = tf.translateYPx * size.height
                                }
                        )
                    }
                }

                // 2. Image / Overlay Layers (with live Ken Burns, Color Filters, Blur, Transitions, Keyframes)
                activeImageClips.forEach { imgClip ->
                    val asset = imgClip.assetId?.let { assetMap[it] } ?: return@forEach
                    val tf = evaluateTransform(imgClip, playheadMs)
                    val colorFilter = remember(imgClip.effects) {
                        buildComposeColorFilter(imgClip.effects)
                    }

                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(Uri.parse(asset.uriString))
                            .crossfade(false)
                            .build(),
                        contentDescription = asset.displayName,
                        contentScale = ContentScale.Fit,
                        colorFilter = colorFilter,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (imgClip.effects.blurRadius > 0.5f) {
                                    Modifier.blur(imgClip.effects.blurRadius.dp)
                                } else Modifier
                            )
                            .graphicsLayer {
                                scaleX = tf.scale
                                scaleY = tf.scale
                                rotationZ = tf.rotationDeg
                                alpha = tf.opacity
                                translationX = ((imgClip.positionX - 0.5f) + tf.translateXPx) * size.width
                                translationY = ((imgClip.positionY - 0.5f) + tf.translateYPx) * size.height
                            }
                    )
                }

                // 3. Empty Visual Gap Indicator (e.g., while listening to story audio before placing an image)
                if (activeVideoClip == null && activeImageClips.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        if (activeAudioClip != null) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = EmeraldAudio,
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = activeAudioClip.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = EmeraldAudio
                            )
                            Text(
                                text = if (isPlaying) {
                                    "Audio playing at ${TimelineEngine.formatMs(playheadMs)} — Pause & add image"
                                } else {
                                    "Audio paused at ${TimelineEngine.formatMs(playheadMs)} — Ready for scene image"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Movie,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "No visual at ${TimelineEngine.formatMs(playheadMs)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                        }
                    }
                }

                // 4. Text & Subtitle Overlay Layer
                activeTextClips.forEach { txtClip ->
                    val style = txtClip.textStyle ?: return@forEach
                    val relProgress = ((playheadMs - txtClip.startMs).toFloat() / 400f).coerceIn(0f, 1f)
                    val displayText = when (style.animation) {
                        com.example.domain.model.TextAnimationPreset.TYPEWRITER -> {
                            val count = (style.text.length * relProgress).toInt().coerceAtLeast(1)
                            style.text.take(count)
                        }
                        else -> style.text
                    }
                    val animAlpha by animateFloatAsState(targetValue = relProgress, label = "textAlpha")
                    val alignBiasX = (style.positionX * 2f - 1f).coerceIn(-0.9f, 0.9f)
                    val alignBiasY = (style.positionY * 2f - 1f).coerceIn(-0.9f, 0.9f)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        val bgMod = if (style.isSubtitle) {
                            Modifier
                                .background(Color(style.backgroundColorHex), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        } else Modifier

                        Text(
                            text = displayText,
                            color = Color(style.textColorHex),
                            fontSize = (style.fontSizeSp * 0.68f).sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(androidx.compose.ui.BiasAlignment(alignBiasX, alignBiasY))
                                .alpha(if (style.animation == com.example.domain.model.TextAnimationPreset.FADE) animAlpha else 1f)
                                .then(bgMod)
                        )
                    }
                }

                // 5. Subtle Aspect Ratio & Timecode HUD badge
                Surface(
                    color = StudioVoid.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(if (isPlaying) CyanPrimary else AmberAccent, CircleShape)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${aspectRatio.widthRatio}:${aspectRatio.heightRatio}  •  ${TimelineEngine.formatMs(playheadMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}

private data class EvaluatedTransform(
    val scale: Float,
    val rotationDeg: Float,
    val opacity: Float,
    val translateXPx: Float,
    val translateYPx: Float
)

private fun evaluateTransform(clip: TimelineClip, playheadMs: Long): EvaluatedTransform {
    val dur = clip.durationMs.coerceAtLeast(1L).toFloat()
    val rel = (playheadMs - clip.startMs).coerceIn(0L, clip.durationMs).toFloat()
    val t = (rel / dur).coerceIn(0f, 1f)

    // Keyframe interpolation if keyframes exist
    var baseScale = clip.scale
    var baseRot = clip.rotationDeg
    var baseOpacity = clip.effects.opacity
    var kfDx = 0f
    var kfDy = 0f

    if (clip.keyframes.isNotEmpty()) {
        val sorted = clip.keyframes.sortedBy { it.timeOffsetMs }
        val prev: Keyframe? = sorted.lastOrNull { it.timeOffsetMs <= rel.toLong() }
        val next: Keyframe? = sorted.firstOrNull { it.timeOffsetMs > rel.toLong() }
        if (prev != null && next != null && next.timeOffsetMs > prev.timeOffsetMs) {
            val f = (rel - prev.timeOffsetMs) / (next.timeOffsetMs - prev.timeOffsetMs).toFloat()
            baseScale = prev.scale + (next.scale - prev.scale) * f
            baseRot = prev.rotationDeg + (next.rotationDeg - prev.rotationDeg) * f
            baseOpacity = prev.opacity + (next.opacity - prev.opacity) * f
            kfDx = (prev.x + (next.x - prev.x) * f) - clip.positionX
            kfDy = (prev.y + (next.y - prev.y) * f) - clip.positionY
        } else {
            val k = prev ?: next!!
            baseScale = k.scale
            baseRot = k.rotationDeg
            baseOpacity = k.opacity
            kfDx = k.x - clip.positionX
            kfDy = k.y - clip.positionY
        }
    }

    // Motion preset
    var motionScale = 1f
    var motionDx = 0f
    var motionDy = 0f
    when (clip.effects.motionEffect) {
        MotionEffectPreset.ZOOM_IN -> motionScale = 1f + 0.18f * t
        MotionEffectPreset.ZOOM_OUT -> motionScale = 1.18f - 0.18f * t
        MotionEffectPreset.PAN_LEFT -> {
            motionScale = 1.12f
            motionDx = -0.06f * t
        }
        MotionEffectPreset.PAN_RIGHT -> {
            motionScale = 1.12f
            motionDx = 0.06f * t
        }
        MotionEffectPreset.KEN_BURNS -> {
            motionScale = 1f + 0.22f * t
            motionDx = 0.04f * t
            motionDy = -0.03f * t
        }
        MotionEffectPreset.NONE -> Unit
    }

    // Transition-in effect
    var transAlpha = 1f
    var transScale = 1f
    var transDx = 0f
    if (clip.transitionIn != TransitionType.NONE && rel < clip.transitionInDurationMs) {
        val p = (rel / clip.transitionInDurationMs.coerceAtLeast(100L)).coerceIn(0f, 1f)
        when (clip.transitionIn) {
            TransitionType.FADE, TransitionType.CROSSFADE, TransitionType.DISSOLVE -> transAlpha = p
            TransitionType.SLIDE_LEFT -> transDx = (1f - p) * 0.6f
            TransitionType.SLIDE_RIGHT -> transDx = -(1f - p) * 0.6f
            TransitionType.ZOOM_IN -> {
                transScale = 0.7f + 0.3f * p
                transAlpha = p
            }
            TransitionType.NONE -> Unit
        }
    }

    return EvaluatedTransform(
        scale = baseScale * motionScale * transScale,
        rotationDeg = baseRot,
        opacity = (baseOpacity * transAlpha).coerceIn(0f, 1f),
        translateXPx = kfDx + motionDx + transDx,
        translateYPx = kfDy + motionDy
    )
}

private fun buildComposeColorFilter(effects: ClipEffects): ColorFilter? {
    val hasAdjustment = kotlin.math.abs(effects.brightness) > 0.01f ||
        kotlin.math.abs(effects.contrast) > 0.01f ||
        kotlin.math.abs(effects.saturation - 1f) > 0.01f ||
        kotlin.math.abs(effects.exposure) > 0.01f ||
        effects.colorFilter != ColorFilterPreset.NONE

    if (!hasAdjustment) return null

    val matrix = ColorMatrix()
    val sat = when (effects.colorFilter) {
        ColorFilterPreset.BW -> 0f
        ColorFilterPreset.VIVID -> (effects.saturation * 1.35f).coerceIn(0f, 2f)
        else -> effects.saturation
    }
    matrix.setToSaturation(sat)

    val contrastScale = 1f + effects.contrast + if (effects.colorFilter == ColorFilterPreset.CINEMA) 0.15f else 0f
    val translate = (effects.brightness + effects.exposure * 0.6f) * 255f + (1f - contrastScale) * 128f

    var rScale = contrastScale
    var gScale = contrastScale
    var bScale = contrastScale
    when (effects.colorFilter) {
        ColorFilterPreset.WARM -> { rScale *= 1.12f; bScale *= 0.88f }
        ColorFilterPreset.COOL -> { bScale *= 1.15f; rScale *= 0.88f }
        ColorFilterPreset.VINTAGE -> { rScale *= 1.1f; gScale *= 1.03f; bScale *= 0.8f }
        ColorFilterPreset.CINEMA -> { gScale *= 1.03f; bScale *= 1.06f }
        else -> Unit
    }

    val adj = ColorMatrix(
        floatArrayOf(
            rScale, 0f, 0f, 0f, translate,
            0f, gScale, 0f, 0f, translate,
            0f, 0f, bScale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
    )
    matrix.timesAssign(adj)
    return ColorFilter.colorMatrix(matrix)
}

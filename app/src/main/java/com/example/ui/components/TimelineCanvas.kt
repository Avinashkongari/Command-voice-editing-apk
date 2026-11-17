package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.GridOff
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.domain.model.MediaAsset
import com.example.domain.model.TimelineClip
import com.example.domain.model.TimelineDocument
import com.example.domain.model.TrackType
import com.example.domain.model.TransitionType
import com.example.engine.TimelineEngine
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldAudio
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.RoseError
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletContainer
import com.example.ui.theme.VioletTrack
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
fun MultiTrackTimeline(
    timeline: TimelineDocument,
    assets: List<MediaAsset>,
    playheadMs: Long,
    zoom: Float,
    selectedClipId: String?,
    highlightedClipId: String?,
    onSeek: (Long) -> Unit,
    onSelectClip: (String?) -> Unit,
    onMoveClip: (String, Long, Long) -> Unit,
    onZoomChange: (Float) -> Unit,
    onToggleSnapping: () -> Unit,
    modifier: Modifier = Modifier
) {
    val assetMap = remember(assets) { assets.associateBy { it.id } }
    val scrollState: ScrollState = rememberScrollState()
    val density = LocalDensity.current
    val basePxPerSecond = 26f * zoom
    val pxPerMs = with(density) { (basePxPerSecond.dp.toPx()) / 1000f }
    val totalTimelineMs = maxOf(timeline.totalDurationMs + 10_000L, 60_000L)
    val contentWidthDp: Dp = ((totalTimelineMs / 1000f) * basePxPerSecond).dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(StudioBackground)
            .border(1.dp, StudioBorder)
    ) {
        // Timeline Controls Strip (Zoom, Snapping, Duration)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(StudioSurface)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (timeline.snappingEnabled) CyanContainer else StudioSurfaceElevated,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onToggleSnapping() }
                        .testTag("timeline_snap_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (timeline.snappingEnabled) Icons.Rounded.GridOn else Icons.Rounded.GridOff,
                            contentDescription = "Snap",
                            tint = if (timeline.snappingEnabled) CyanPrimary else TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (timeline.snappingEnabled) "Snap ON" else "Snap OFF",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (timeline.snappingEnabled) CyanPrimary else TextMuted
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${timeline.clips.size} clips",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onZoomChange(zoom - 0.35f) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ZoomOut,
                        contentDescription = "Zoom out timeline",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "${String.format("%.1f", zoom)}x",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary
                )
                IconButton(
                    onClick = { onZoomChange(zoom + 0.35f) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ZoomIn,
                        contentDescription = "Zoom in timeline",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Track Labels Column + Horizontally Scrollable Ruler & Multi-Track Canvas
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Left Fixed Track Headers
            Column(
                modifier = Modifier
                    .width(52.dp)
                    .fillMaxHeight()
                    .background(StudioSurface)
                    .border(1.dp, StudioBorder)
            ) {
                Box(modifier = Modifier.height(24.dp)) // Ruler spacer
                TrackHeaderCell(Icons.Rounded.Videocam, "Video", CyanPrimary)
                TrackHeaderCell(Icons.Rounded.Image, "Image", AmberAccent)
                TrackHeaderCell(Icons.Rounded.Subtitles, "Text", VioletTrack)
                TrackHeaderCell(Icons.Rounded.AutoAwesome, "FX", RoseError)
                TrackHeaderCell(Icons.Rounded.GraphicEq, "Audio", EmeraldAudio)
                TrackHeaderCell(Icons.Rounded.Mic, "Voice", EmeraldAudio)
            }

            // Scrollable Multi-Track Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(scrollState)
            ) {
                Column(
                    modifier = Modifier
                        .width(contentWidthDp)
                        .fillMaxHeight()
                ) {
                    // Time Ruler (tap or drag to scrub playhead)
                    TimeRulerRow(
                        totalMs = totalTimelineMs,
                        pxPerMs = pxPerMs,
                        onSeek = onSeek
                    )

                    val tracks = listOf(
                        TrackType.VIDEO_MAIN,
                        TrackType.IMAGE_OVERLAY,
                        TrackType.TEXT_SUBTITLE,
                        TrackType.EFFECT_OVERLAY,
                        TrackType.AUDIO_MAIN,
                        TrackType.AUDIO_VOICEOVER
                    )

                    tracks.forEach { trackType ->
                        val trackClips = remember(timeline.clips, trackType) {
                            timeline.clips.filter { it.trackType == trackType }
                        }
                        TrackLaneRow(
                            trackType = trackType,
                            clips = trackClips,
                            assetMap = assetMap,
                            pxPerMs = pxPerMs,
                            selectedClipId = selectedClipId,
                            highlightedClipId = highlightedClipId,
                            onSelectClip = onSelectClip,
                            onMoveClip = onMoveClip,
                            onTapLane = { tapX ->
                                onSelectClip(null)
                                onSeek((tapX / pxPerMs).roundToLong())
                            }
                        )
                    }
                }

                // Vertical Cyan Playhead Needle Overlay
                val playheadX = (playheadMs * pxPerMs).roundToInt()
                Box(
                    modifier = Modifier
                        .offset { IntOffset(playheadX - 8, 0) }
                        .width(16.dp)
                        .fillMaxHeight()
                        .pointerInput(pxPerMs) {
                            var dragStartMs = playheadMs
                            var accumulatedDragPx = 0f
                            detectDragGestures(
                                onDragStart = {
                                    dragStartMs = playheadMs
                                    accumulatedDragPx = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    accumulatedDragPx += dragAmount.x
                                    val targetMs = dragStartMs + (accumulatedDragPx / pxPerMs).roundToLong()
                                    onSeek(targetMs.coerceIn(0L, totalTimelineMs))
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = size.width / 2f
                        // Needle head triangle
                        val headPath = Path().apply {
                            moveTo(cx - 7f, 0f)
                            lineTo(cx + 7f, 0f)
                            lineTo(cx + 7f, 12f)
                            lineTo(cx, 20f)
                            lineTo(cx - 7f, 12f)
                            close()
                        }
                        drawPath(headPath, color = CyanPrimary)
                        drawLine(
                            color = CyanPrimary,
                            start = Offset(cx, 18f),
                            end = Offset(cx, size.height),
                            strokeWidth = 3f
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackHeaderCell(icon: ImageVector, label: String, tint: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .border(0.5.dp, StudioBorder.copy(alpha = 0.5f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            fontSize = androidx.compose.ui.unit.TextUnit.Unspecified
        )
    }
}

@Composable
private fun TimeRulerRow(
    totalMs: Long,
    pxPerMs: Float,
    onSeek: (Long) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(StudioVoid)
            .pointerInput(pxPerMs) {
                detectTapGestures { offset ->
                    onSeek((offset.x / pxPerMs).roundToLong())
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stepMs = when {
                pxPerMs > 0.08f -> 2000L
                pxPerMs > 0.04f -> 5000L
                else -> 10_000L
            }
            var t = 0L
            while (t <= totalMs) {
                val x = t * pxPerMs
                drawLine(
                    color = StudioBorder,
                    start = Offset(x, size.height * 0.45f),
                    end = Offset(x, size.height),
                    strokeWidth = 2f
                )
                t += stepMs
            }
        }

        // Timestamp labels at major intervals
        val labelStepMs = when {
            pxPerMs > 0.08f -> 5000L
            pxPerMs > 0.04f -> 10_000L
            else -> 20_000L
        }
        val density = LocalDensity.current
        var t = 0L
        while (t <= totalMs) {
            val xDp = with(density) { (t * pxPerMs).toDp() }
            Text(
                text = TimelineEngine.formatMs(t),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier
                    .offset(x = xDp + 3.dp, y = 2.dp)
            )
            t += labelStepMs
        }
    }
}

@Composable
private fun TrackLaneRow(
    trackType: TrackType,
    clips: List<TimelineClip>,
    assetMap: Map<String, MediaAsset>,
    pxPerMs: Float,
    selectedClipId: String?,
    highlightedClipId: String?,
    onSelectClip: (String?) -> Unit,
    onMoveClip: (String, Long, Long) -> Unit,
    onTapLane: (Float) -> Unit
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(StudioBackground)
            .border(0.5.dp, StudioBorder.copy(alpha = 0.45f))
            .pointerInput(pxPerMs) {
                detectTapGestures { offset ->
                    onTapLane(offset.x)
                }
            }
    ) {
        clips.forEach { clip ->
            val leftDp = with(density) { (clip.startMs * pxPerMs).toDp() }
            val widthDp = with(density) { (clip.durationMs * pxPerMs).toDp() }.coerceAtLeast(28.dp)
            val isSelected = clip.id == selectedClipId
            val isHighlighted = clip.id == highlightedClipId
            val asset = clip.assetId?.let { assetMap[it] }

            val (bgColor, accentColor) = when (trackType) {
                TrackType.VIDEO_MAIN -> CyanContainer to CyanPrimary
                TrackType.IMAGE_OVERLAY -> AmberContainer to AmberAccent
                TrackType.TEXT_SUBTITLE -> VioletContainer to VioletTrack
                TrackType.EFFECT_OVERLAY -> Color(0xFF3A1A2E) to RoseError
                TrackType.AUDIO_MAIN, TrackType.AUDIO_VOICEOVER -> EmeraldContainer to EmeraldAudio
            }

            val borderCol by animateColorAsState(
                targetValue = when {
                    isHighlighted -> Color.White
                    isSelected -> accentColor
                    else -> accentColor.copy(alpha = 0.45f)
                },
                label = "clipBorder"
            )

            Box(
                modifier = Modifier
                    .offset(x = leftDp, y = 3.dp)
                    .width(widthDp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(bgColor)
                    .border(if (isSelected || isHighlighted) 2.dp else 1.dp, borderCol, RoundedCornerShape(6.dp))
                    .pointerInput(clip.id, pxPerMs) {
                        detectTapGestures { onSelectClip(clip.id) }
                    }
                    .pointerInput(clip.id, pxPerMs) {
                        var dragStartMs = clip.startMs
                        var accumulatedDragPx = 0f
                        detectDragGestures(
                            onDragStart = {
                                dragStartMs = clip.startMs
                                accumulatedDragPx = 0f
                                onSelectClip(clip.id)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                accumulatedDragPx += dragAmount.x
                                val newStart = (dragStartMs + (accumulatedDragPx / pxPerMs).roundToLong()).coerceAtLeast(0L)
                                onMoveClip(clip.id, newStart, newStart + clip.durationMs)
                            }
                        )
                    }
            ) {
                // Audio Waveform Canvas inside Audio Clips
                if ((trackType == TrackType.AUDIO_MAIN || trackType == TrackType.AUDIO_VOICEOVER) &&
                    asset != null && asset.waveformPeaks.isNotEmpty()
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(vertical = 3.dp)) {
                        val peaks = asset.waveformPeaks
                        val stepW = size.width / peaks.size.coerceAtLeast(1)
                        val midY = size.height / 2f
                        peaks.forEachIndexed { idx, amp ->
                            val x = idx * stepW + stepW / 2f
                            val barH = (amp * size.height * 0.85f).coerceAtLeast(2f)
                            drawLine(
                                color = EmeraldAudio.copy(alpha = 0.72f),
                                start = Offset(x, midY - barH / 2f),
                                end = Offset(x, midY + barH / 2f),
                                strokeWidth = (stepW * 0.65f).coerceIn(1.5f, 4f)
                            )
                        }
                    }
                }

                // Clip Label & Badges
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = clip.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (clip.speed != 1f) {
                        Text(
                            text = "${clip.speed}x",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberAccent,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                    if (clip.transitionIn != TransitionType.NONE) {
                        Box(
                            modifier = Modifier
                                .padding(start = 3.dp)
                                .size(7.dp)
                                .background(AmberAccent, CircleShape)
                        )
                    }
                }

                // Keyframe diamonds along bottom edge of clip
                clip.keyframes.forEach { kf ->
                    val kfDp = with(density) { (kf.timeOffsetMs * pxPerMs).toDp() }
                    Box(
                        modifier = Modifier
                            .offset(x = kfDp, y = 26.dp)
                            .size(6.dp)
                            .background(AmberAccent, CircleShape)
                    )
                }
            }
        }
    }
}

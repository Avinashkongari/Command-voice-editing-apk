package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.domain.model.ColorFilterPreset
import com.example.domain.model.CropRect
import com.example.domain.model.MotionEffectPreset
import com.example.domain.model.TextAnimationPreset
import com.example.domain.model.TimelineClip
import com.example.domain.model.TransitionType
import com.example.engine.TimelineEngine
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.OnCyan
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

enum class EditorInspectorTab(val title: String) {
    TIMING("Trim & Timing"),
    TRANSFORM("Transform & Crop"),
    EFFECTS("Effects & Filters"),
    TRANSITIONS("Transitions"),
    SPEED("Speed"),
    AUDIO("Audio & Fades"),
    TEXT("Text & Subtitles"),
    KEYFRAMES("Keyframes")
}

@Composable
fun ManualInspectorSheet(
    tab: EditorInspectorTab,
    selectedClip: TimelineClip?,
    playheadMs: Long,
    onClose: () -> Unit,
    onUpdateTiming: (Long, Long) -> Unit,
    onUpdateEffects: (
        brightness: Float?, contrast: Float?, saturation: Float?,
        exposure: Float?, blur: Float?, sharpen: Float?,
        opacity: Float?, filter: ColorFilterPreset?, motion: MotionEffectPreset?
    ) -> Unit,
    onUpdateTransform: (scale: Float?, x: Float?, y: Float?, stretchX: Float?, stretchY: Float?, crop: CropRect?) -> Unit,
    onRotate: (Float) -> Unit,
    onUpdateTransition: (TransitionType, Long) -> Unit,
    onUpdateSpeed: (Float) -> Unit,
    onUpdateVolume: (Float, Long?, Long?) -> Unit,
    onAddText: (String, Boolean, Float, Long, TextAnimationPreset) -> Unit,
    onUpdateText: (String, Float, Long, TextAnimationPreset) -> Unit,
    onAddKeyframe: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = StudioSurface,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, StudioBorder, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = tab.title + (selectedClip?.let { " • ${it.label}" } ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close inspector", tint = TextSecondary)
                }
            }

            Spacer(Modifier.height(10.dp))

            if (selectedClip == null && tab != EditorInspectorTab.TEXT) {
                Text(
                    text = "Select a clip on the timeline first to adjust ${tab.title}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            } else {
                when (tab) {
                    EditorInspectorTab.TIMING -> selectedClip?.let { clip ->
                        TimingInspector(clip, onUpdateTiming)
                    }
                    EditorInspectorTab.TRANSFORM -> selectedClip?.let { clip ->
                        TransformCropInspector(clip, onUpdateTransform, onRotate)
                    }
                    EditorInspectorTab.EFFECTS -> selectedClip?.let { clip ->
                        EffectsInspector(clip, onUpdateEffects)
                    }
                    EditorInspectorTab.TRANSITIONS -> selectedClip?.let { clip ->
                        TransitionsInspector(clip, onUpdateTransition)
                    }
                    EditorInspectorTab.SPEED -> selectedClip?.let { clip ->
                        SpeedInspector(clip, onUpdateSpeed)
                    }
                    EditorInspectorTab.AUDIO -> selectedClip?.let { clip ->
                        AudioInspector(clip, onUpdateVolume)
                    }
                    EditorInspectorTab.TEXT -> TextInspector(selectedClip, onAddText, onUpdateText)
                    EditorInspectorTab.KEYFRAMES -> selectedClip?.let { clip ->
                        KeyframeInspector(clip, playheadMs, onAddKeyframe)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimingInspector(clip: TimelineClip, onUpdateTiming: (Long, Long) -> Unit) {
    var startSec by remember(clip.id, clip.startMs) { mutableStateOf((clip.startMs / 1000.0).toString()) }
    var endSec by remember(clip.id, clip.endMs) { mutableStateOf((clip.endMs / 1000.0).toString()) }

    Text(
        text = "Current Span: ${TimelineEngine.formatMs(clip.startMs)} → ${TimelineEngine.formatMs(clip.endMs)} (${clip.durationMs / 1000f}s)",
        style = MaterialTheme.typography.labelMedium,
        color = CyanPrimary
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = startSec,
            onValueChange = { startSec = it },
            label = { Text("Start (sec)") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = endSec,
            onValueChange = { endSec = it },
            label = { Text("End (sec)") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onUpdateTiming(clip.startMs, clip.endMs - 1000L) }, modifier = Modifier.weight(1f)) {
            Text("-1s End")
        }
        OutlinedButton(onClick = { onUpdateTiming(clip.startMs, clip.endMs + 1000L) }, modifier = Modifier.weight(1f)) {
            Text("+1s End")
        }
        Button(
            onClick = {
                val s = ((startSec.toDoubleOrNull() ?: 0.0) * 1000.0).toLong().coerceAtLeast(0L)
                val e = ((endSec.toDoubleOrNull() ?: 5.0) * 1000.0).toLong().coerceAtLeast(s + 250L)
                onUpdateTiming(s, e)
            },
            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = OnCyan),
            modifier = Modifier.weight(1f)
        ) {
            Text("Apply")
        }
    }
}

@Composable
private fun TransformCropInspector(
    clip: TimelineClip,
    onUpdateTransform: (Float?, Float?, Float?, Float?, Float?, CropRect?) -> Unit,
    onRotate: (Float) -> Unit
) {
    var scale by remember(clip.id) { mutableFloatStateOf(clip.scale) }
    var posX by remember(clip.id) { mutableFloatStateOf(clip.positionX) }
    var posY by remember(clip.id) { mutableFloatStateOf(clip.positionY) }
    var stretchX by remember(clip.id) { mutableFloatStateOf(clip.stretchX) }
    var stretchY by remember(clip.id) { mutableFloatStateOf(clip.stretchY) }
    var cropMargin by remember(clip.id) { mutableFloatStateOf(clip.crop.left) }

    LabeledSlider("Scale", scale, 0.3f..2.5f, "${String.format("%.2f", scale)}x") {
        scale = it
        onUpdateTransform(it, null, null, null, null, null)
    }
    LabeledSlider("Horizontal Pos", posX, 0f..1f, "${(posX * 100).toInt()}%") {
        posX = it
        onUpdateTransform(null, it, null, null, null, null)
    }
    LabeledSlider("Vertical Pos", posY, 0f..1f, "${(posY * 100).toInt()}%") {
        posY = it
        onUpdateTransform(null, null, it, null, null, null)
    }
    LabeledSlider("Crop Inset", cropMargin, 0f..0.35f, "${(cropMargin * 100).toInt()}%") {
        cropMargin = it
        onUpdateTransform(null, null, null, null, null, CropRect(it, it, 1f - it, 1f - it))
    }
    LabeledSlider("Horizontal Stretch", stretchX, 0.25f..3f, "${String.format("%.2f", stretchX)}x") {
        stretchX = it
        onUpdateTransform(null, null, null, it, null, null)
    }
    LabeledSlider("Vertical Stretch", stretchY, 0.25f..3f, "${String.format("%.2f", stretchY)}x") {
        stretchY = it
        onUpdateTransform(null, null, null, null, it, null)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onRotate(90f) }, modifier = Modifier.weight(1f)) { Text("Rotate +90°") }
        OutlinedButton(onClick = { onRotate(-90f) }, modifier = Modifier.weight(1f)) { Text("Rotate -90°") }
    }
}

@Composable
private fun EffectsInspector(
    clip: TimelineClip,
    onUpdateEffects: (Float?, Float?, Float?, Float?, Float?, Float?, Float?, ColorFilterPreset?, MotionEffectPreset?) -> Unit
) {
    val e = clip.effects
    Text("Motion / Ken Burns", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MotionEffectPreset.entries.forEach { preset ->
            val selected = e.motionEffect == preset
            ChoiceChipPill(preset.displayName, selected) {
                onUpdateEffects(null, null, null, null, null, null, null, null, preset)
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    Text("Color Filters", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ColorFilterPreset.entries.forEach { filter ->
            val selected = e.colorFilter == filter
            ChoiceChipPill(filter.displayName, selected) {
                onUpdateEffects(null, null, null, null, null, null, null, filter, null)
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    LabeledSlider("Brightness", e.brightness, -0.8f..0.8f, String.format("%+.2f", e.brightness)) {
        onUpdateEffects(it, null, null, null, null, null, null, null, null)
    }
    LabeledSlider("Contrast", e.contrast, -0.8f..0.8f, String.format("%+.2f", e.contrast)) {
        onUpdateEffects(null, it, null, null, null, null, null, null, null)
    }
    LabeledSlider("Saturation", e.saturation, 0f..2f, "${(e.saturation * 100).toInt()}%") {
        onUpdateEffects(null, null, it, null, null, null, null, null, null)
    }
    LabeledSlider("Exposure", e.exposure, -0.8f..0.8f, String.format("%+.2f", e.exposure)) {
        onUpdateEffects(null, null, null, it, null, null, null, null, null)
    }
    LabeledSlider("Blur", e.blurRadius, 0f..20f, "${e.blurRadius.toInt()}px") {
        onUpdateEffects(null, null, null, null, it, null, null, null, null)
    }
    LabeledSlider("Opacity", e.opacity, 0f..1f, "${(e.opacity * 100).toInt()}%") {
        onUpdateEffects(null, null, null, null, null, null, it, null, null)
    }
}

@Composable
private fun TransitionsInspector(clip: TimelineClip, onUpdateTransition: (TransitionType, Long) -> Unit) {
    var dur by remember(clip.id) { mutableFloatStateOf(clip.transitionInDurationMs.toFloat()) }
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TransitionType.entries.forEach { tr ->
            ChoiceChipPill(tr.displayName, clip.transitionIn == tr) {
                onUpdateTransition(tr, dur.toLong())
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    LabeledSlider("Transition Duration", dur, 200f..2000f, "${dur.toInt()}ms") {
        dur = it
        onUpdateTransition(clip.transitionIn, it.toLong())
    }
}

@Composable
private fun SpeedInspector(clip: TimelineClip, onUpdateSpeed: (Float) -> Unit) {
    val presets = listOf(0.25f, 0.5f, 1f, 1.5f, 2f, 3f, 4f)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        presets.forEach { spd ->
            ChoiceChipPill("${spd}x", kotlin.math.abs(clip.speed - spd) < 0.05f) {
                onUpdateSpeed(spd)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    LabeledSlider("Custom Speed", clip.speed, 0.25f..4f, "${String.format("%.2f", clip.speed)}x") {
        onUpdateSpeed(it)
    }
}

@Composable
private fun AudioInspector(clip: TimelineClip, onUpdateVolume: (Float, Long?, Long?) -> Unit) {
    LabeledSlider("Volume", clip.volume, 0f..2f, "${(clip.volume * 100).toInt()}%") {
        onUpdateVolume(it, null, null)
    }
    LabeledSlider("Fade In", clip.fadeInMs.toFloat(), 0f..4000f, "${clip.fadeInMs}ms") {
        onUpdateVolume(clip.volume, it.toLong(), null)
    }
    LabeledSlider("Fade Out", clip.fadeOutMs.toFloat(), 0f..4000f, "${clip.fadeOutMs}ms") {
        onUpdateVolume(clip.volume, null, it.toLong())
    }
}

@Composable
private fun TextInspector(
    selectedClip: TimelineClip?,
    onAddText: (String, Boolean, Float, Long, TextAnimationPreset) -> Unit,
    onUpdateText: (String, Float, Long, TextAnimationPreset) -> Unit
) {
    val existing = selectedClip?.textStyle
    var text by remember(selectedClip?.id) { mutableStateOf(existing?.text ?: "") }
    var isSub by remember(selectedClip?.id) { mutableStateOf(existing?.isSubtitle ?: false) }
    var sizeSp by remember(selectedClip?.id) { mutableFloatStateOf(existing?.fontSizeSp ?: 26f) }
    var colorHex by remember(selectedClip?.id) { mutableLongStateOf(existing?.textColorHex ?: 0xFFFFFFFF) }
    var anim by remember(selectedClip?.id) { mutableStateOf(existing?.animation ?: TextAnimationPreset.FADE) }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("Title or Subtitle Text") },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChipPill("Title Overlay", !isSub) { isSub = false }
        ChoiceChipPill("Subtitle Caption", isSub) { isSub = true }
    }
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TextAnimationPreset.entries.forEach { preset ->
            ChoiceChipPill(preset.displayName, anim == preset) { anim = preset }
        }
    }
    Spacer(Modifier.height(8.dp))
    val colors = listOf(0xFFFFFFFFL, 0xFF00E5FFL, 0xFFFFB300L, 0xFFFF4D6DL, 0xFF2EE59DL)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Color:", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        colors.forEach { hex ->
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(hex))
                    .border(if (colorHex == hex) 2.dp else 1.dp, if (colorHex == hex) CyanPrimary else StudioBorder, RoundedCornerShape(8.dp))
                    .clickable { colorHex = hex }
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    LabeledSlider("Font Size", sizeSp, 14f..48f, "${sizeSp.toInt()}sp") { sizeSp = it }
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            if (existing != null) onUpdateText(text, sizeSp, colorHex, anim)
            else onAddText(text, isSub, sizeSp, colorHex, anim)
        },
        enabled = text.isNotBlank(),
        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = OnCyan),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (existing != null) "Update Text Clip" else "Add to Timeline at Playhead")
    }
}

@Composable
private fun KeyframeInspector(clip: TimelineClip, playheadMs: Long, onAddKeyframe: () -> Unit) {
    Text(
        text = "${clip.keyframes.size} keyframe(s) on this clip. Adjust Transform/Opacity/Volume at any playhead position and tap Add Keyframe.",
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = onAddKeyframe,
        colors = ButtonDefaults.buttonColors(containerColor = AmberAccent, contentColor = Color.Black),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Rounded.Diamond, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Set Keyframe at ${TimelineEngine.formatMs(playheadMs)}")
    }
    if (clip.keyframes.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        clip.keyframes.forEach { kf ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .background(StudioSurfaceElevated, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "@ ${TimelineEngine.formatMs(kf.timeOffsetMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = AmberAccent
                )
                Text(
                    text = "Scale ${String.format("%.2f", kf.scale)} • Opacity ${(kf.opacity * 100).toInt()}% • Rot ${kf.rotationDeg.toInt()}°",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = CyanPrimary)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = CyanPrimary,
                activeTrackColor = CyanPrimary,
                inactiveTrackColor = StudioSurfaceElevated
            )
        )
    }
}

@Composable
private fun ChoiceChipPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) CyanContainer else StudioSurfaceElevated,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (selected) CyanPrimary else StudioBorder, RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) CyanPrimary else TextSecondary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

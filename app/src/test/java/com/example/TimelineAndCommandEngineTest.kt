package com.example

import com.example.ai.engine.AiResponseParser
import com.example.ai.engine.CommandValidator
import com.example.ai.engine.ValidationResult
import com.example.domain.model.AiEditorOperation
import com.example.domain.model.MediaAsset
import com.example.domain.model.MediaAssetType
import com.example.domain.model.MotionEffectPreset
import com.example.domain.model.TimelineDocument
import com.example.domain.model.TrackType
import com.example.domain.model.TransitionType
import com.example.engine.TimelineEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineAndCommandEngineTest {

    private val sampleImageAsset = MediaAsset(
        id = "img_001",
        projectId = "proj_1",
        uriString = "content://media/external/images/1",
        displayName = "blue_character.png",
        type = MediaAssetType.IMAGE,
        tags = "blue character hero"
    )

    @Test
    fun `parses fenced JSON command envelope accurately`() {
        val raw = """
            ```json
            {
              "reply": "Image 1:20 se 1:38 tak add kar di gayi hai.",
              "status_preview": "Image timeline mein add kar raha hoon...",
              "operations": [
                {
                  "operation": "add_image",
                  "asset_id": "img_001",
                  "start_ms": 80000,
                  "end_ms": 98000,
                  "track": "image_overlay"
                }
              ]
            }
            ```
        """.trimIndent()

        val parsed = AiResponseParser.parse(raw).getOrThrow()
        assertEquals(1, parsed.operations.size)
        assertEquals("add_image", parsed.operations.first().operation)
        assertEquals(80_000L, parsed.operations.first().start_ms)
        assertEquals(98_000L, parsed.operations.first().end_ms)
    }

    @Test
    fun `validator blocks missing asset and requests user selection`() {
        val ops = listOf(
            AiEditorOperation(
                operation = "add_image",
                asset_id = "non_existent_id",
                start_ms = 10_000L,
                end_ms = 15_000L
            )
        )
        val result = CommandValidator.validate(
            operations = ops,
            timeline = TimelineDocument(),
            assets = emptyList(),
            playheadMs = 0L,
            selectedClipId = null,
            selectedAssetId = null
        )
        assertTrue(result is ValidationResult.MissingAsset)
        assertEquals(MediaAssetType.IMAGE, (result as ValidationResult.MissingAsset).assetType)
    }

    @Test
    fun `validator rejects end timestamp before start timestamp`() {
        val ops = listOf(
            AiEditorOperation(
                operation = "add_image",
                asset_id = "img_001",
                start_ms = 50_000L,
                end_ms = 40_000L
            )
        )
        val result = CommandValidator.validate(
            operations = ops,
            timeline = TimelineDocument(),
            assets = listOf(sampleImageAsset),
            playheadMs = 0L,
            selectedClipId = null,
            selectedAssetId = "img_001"
        )
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `timeline engine executes add_image, effect, transition, split, and undo redo`() {
        val engine = TimelineEngine()

        // 1. Add image from 80s to 98s (18s duration)
        val addRes = engine.executeAiOperations(
            operations = listOf(
                AiEditorOperation(
                    operation = "add_image",
                    asset_id = "img_001",
                    start_ms = 80_000L,
                    end_ms = 98_000L,
                    track = "image_overlay"
                )
            ),
            assets = listOf(sampleImageAsset),
            playheadMs = 0L
        )
        assertEquals(1, addRes.document.clips.size)
        val clip = addRes.document.clips.first()
        assertEquals(80_000L, clip.startMs)
        assertEquals(98_000L, clip.endMs)
        assertEquals(TrackType.IMAGE_OVERLAY, clip.trackType)

        // 2. Apply slow zoom & smooth crossfade transition
        val fxRes = engine.executeAiOperations(
            operations = listOf(
                AiEditorOperation(operation = "add_effect", clip_id = clip.id, effect = "zoom_in"),
                AiEditorOperation(operation = "add_transition", clip_id = clip.id, transition = "CROSSFADE")
            ),
            assets = listOf(sampleImageAsset),
            playheadMs = 82_000L
        )
        val updatedClip = fxRes.document.clips.first()
        assertEquals(MotionEffectPreset.ZOOM_IN, updatedClip.effects.motionEffect)
        assertEquals(TransitionType.CROSSFADE, updatedClip.transitionIn)

        // 3. Split at 89s
        val splitRes = engine.splitClip(updatedClip.id, 89_000L)!!
        assertEquals(2, splitRes.document.clips.size)
        assertEquals(89_000L, splitRes.document.clips[0].endMs)
        assertEquals(89_000L, splitRes.document.clips[1].startMs)

        // 4. Undo split & redo
        val undone = engine.undo()!!
        assertEquals(1, undone.document.clips.size)
        assertTrue(engine.canRedo)

        val redone = engine.redo()!!
        assertEquals(2, redone.document.clips.size)
        assertFalse(engine.canRedo)
    }
}

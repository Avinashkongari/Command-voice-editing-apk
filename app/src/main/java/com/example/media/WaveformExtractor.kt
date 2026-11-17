package com.example.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Memory-efficient streaming waveform extractor for long audio files (15+ minutes).
 * Uses MediaExtractor + MediaCodec and samples sparse PCM chunks into a compact list of peaks.
 */
class WaveformExtractor(private val context: Context) {

    suspend fun extractPeaks(
        uri: Uri,
        targetBars: Int = 180
    ): List<Float> = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = fmt
                    break
                }
            }
            if (audioTrackIndex < 0 || audioFormat == null) return@withContext defaultWave(targetBars)

            extractor.selectTrack(audioTrackIndex)
            val mime = audioFormat.getString(MediaFormat.KEY_MIME).orEmpty()
            val durationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION)
            } else 0L

            // For very long audio (e.g. 15 min), seek across evenly spaced windows instead of decoding all samples!
            if (durationUs > 20_000_000L) {
                return@withContext extractSampledWindows(extractor, audioFormat, mime, durationUs, targetBars)
            }

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(audioFormat, null, null, 0)
                start()
            }

            val rawPeaks = mutableListOf<Float>()
            val info = MediaCodec.BufferInfo()
            var isEos = false
            var decodedFrames = 0

            while (!isEos && decodedFrames < 8000) {
                val inIdx = codec.dequeueInputBuffer(5000)
                if (inIdx >= 0) {
                    val buf = codec.getInputBuffer(inIdx)
                    if (buf != null) {
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEos = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                var outIdx = codec.dequeueOutputBuffer(info, 5000)
                while (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null && info.size > 0) {
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        var maxAmp = 0
                        while (shortBuf.hasRemaining()) {
                            val amp = abs(shortBuf.get().toInt())
                            if (amp > maxAmp) maxAmp = amp
                        }
                        rawPeaks.add((maxAmp / 32768f).coerceIn(0.05f, 1f))
                        decodedFrames++
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEos = true
                        break
                    }
                    outIdx = codec.dequeueOutputBuffer(info, 0)
                }
            }

            if (rawPeaks.isEmpty()) defaultWave(targetBars) else downsample(rawPeaks, targetBars)
        } catch (_: Exception) {
            defaultWave(targetBars)
        } finally {
            runCatching { codec?.stop(); codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun extractSampledWindows(
        extractor: MediaExtractor,
        audioFormat: MediaFormat,
        mime: String,
        durationUs: Long,
        targetBars: Int
    ): List<Float> {
        val codec = MediaCodec.createDecoderByType(mime)
        return try {
            codec.configure(audioFormat, null, null, 0)
            codec.start()
            val peaks = ArrayList<Float>(targetBars)
            val info = MediaCodec.BufferInfo()
            val stepUs = (durationUs / targetBars.coerceAtLeast(1)).coerceAtLeast(1000L)

            for (i in 0 until targetBars) {
                val targetUs = i * stepUs
                extractor.seekTo(targetUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                codec.flush()

                var windowPeak = 0.08f
                var tries = 0
                while (tries < 6) {
                    val inIdx = codec.dequeueInputBuffer(3000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)
                        if (buf != null) {
                            val size = extractor.readSampleData(buf, 0)
                            if (size >= 0) {
                                codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            } else {
                                codec.queueInputBuffer(inIdx, 0, 0, 0L, 0)
                                break
                            }
                        }
                    }
                    val outIdx = codec.dequeueOutputBuffer(info, 4000)
                    if (outIdx >= 0) {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && info.size > 0) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            var maxAmp = 0
                            while (shortBuf.hasRemaining()) {
                                val amp = abs(shortBuf.get().toInt())
                                if (amp > maxAmp) maxAmp = amp
                            }
                            windowPeak = (maxAmp / 32768f).coerceIn(0.06f, 1f)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        break
                    }
                    tries++
                }
                peaks.add(windowPeak)
            }
            peaks
        } finally {
            runCatching { codec.stop(); codec.release() }
        }
    }

    private fun downsample(raw: List<Float>, targetBars: Int): List<Float> {
        if (raw.size <= targetBars) return raw
        val bucketSize = raw.size.toFloat() / targetBars
        return List(targetBars) { idx ->
            val start = (idx * bucketSize).toInt()
            val end = (((idx + 1) * bucketSize).toInt()).coerceIn(start + 1, raw.size)
            raw.subList(start, end).maxOrNull() ?: 0.1f
        }
    }

    private fun defaultWave(count: Int): List<Float> =
        List(count) { i -> (0.25f + 0.45f * abs(kotlin.math.sin(i * 0.4f))).toFloat() }
}

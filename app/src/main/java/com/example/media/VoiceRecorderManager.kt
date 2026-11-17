package com.example.media

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

sealed interface VoiceRecorderState {
    data object Idle : VoiceRecorderState
    data class Recording(val elapsedMs: Long, val amplitudeNormalized: Float) : VoiceRecorderState
    data class Error(val message: String) : VoiceRecorderState
}

class VoiceRecorderManager(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var meterJob: Job? = null
    private var startTimeMs: Long = 0L

    private val _state = MutableStateFlow<VoiceRecorderState>(VoiceRecorderState.Idle)
    val state: StateFlow<VoiceRecorderState> = _state.asStateFlow()

    fun startRecording(scope: CoroutineScope): Boolean {
        stopAndDiscard()
        return try {
            val dir = File(context.filesDir, "voiceovers").apply { mkdirs() }
            val file = File(dir, "voice_${UUID.randomUUID()}.m4a")
            outputFile = file

            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            rec.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = rec
            startTimeMs = System.currentTimeMillis()
            _state.value = VoiceRecorderState.Recording(0L, 0f)

            meterJob = scope.launch(Dispatchers.Main) {
                while (isActive && recorder != null) {
                    val amp = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                    val norm = (amp / 32767f).coerceIn(0f, 1f)
                    _state.value = VoiceRecorderState.Recording(
                        elapsedMs = System.currentTimeMillis() - startTimeMs,
                        amplitudeNormalized = norm
                    )
                    delay(80L)
                }
            }
            true
        } catch (e: Exception) {
            _state.value = VoiceRecorderState.Error(e.message ?: "Failed to start microphone recording.")
            stopAndDiscard()
            false
        }
    }

    fun stopAndSave(): Pair<Uri, Long>? {
        meterJob?.cancel()
        meterJob = null
        val file = outputFile
        val elapsed = System.currentTimeMillis() - startTimeMs
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            _state.value = VoiceRecorderState.Idle
            if (file != null && file.exists() && file.length() > 0) {
                Uri.fromFile(file) to elapsed.coerceAtLeast(500L)
            } else null
        } catch (e: Exception) {
            runCatching { recorder?.release() }
            recorder = null
            _state.value = VoiceRecorderState.Error(e.message ?: "Recording save failed.")
            null
        }
    }

    fun stopAndDiscard() {
        meterJob?.cancel()
        meterJob = null
        runCatching {
            recorder?.stop()
        }
        runCatching {
            recorder?.release()
        }
        recorder = null
        outputFile?.delete()
        outputFile = null
        _state.value = VoiceRecorderState.Idle
    }
}

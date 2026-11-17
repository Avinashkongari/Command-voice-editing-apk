package com.example.ai.engine

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VoiceLanguageMode(val label: String, val localeTag: String) {
    HINGLISH_AUTO("Auto (EN/HI)", "en-IN"),
    HINDI("Hindi (हिन्दी)", "hi-IN"),
    ENGLISH("English", "en-US")
}

sealed interface VoiceRecognitionState {
    data object Idle : VoiceRecognitionState
    data class Listening(val partialText: String, val rmsDb: Float) : VoiceRecognitionState
    data class ResultReady(val transcript: String) : VoiceRecognitionState
    data class Error(val message: String) : VoiceRecognitionState
}

/**
 * Segmented continuous speech recognition. Android SpeechRecognizer normally ends a
 * single utterance after a short pause; this wrapper restarts recognition between
 * utterances until the user explicitly presses Stop.
 */
class VoiceCommandRecognizer(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var recognitionIntent: Intent? = null
    private var sessionActive = false
    private var manualStopRequested = false
    private var segmentRunning = false
    private val completedSegments = mutableListOf<String>()

    private val _state = MutableStateFlow<VoiceRecognitionState>(VoiceRecognitionState.Idle)
    val state: StateFlow<VoiceRecognitionState> = _state.asStateFlow()

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(languageMode: VoiceLanguageMode) {
        cancelListening()
        if (!isAvailable()) {
            _state.value = VoiceRecognitionState.Error(
                "Speech recognition service is not installed. Please type your command or install a speech recognition service."
            )
            return
        }

        completedSegments.clear()
        manualStopRequested = false
        sessionActive = true

        recognitionIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageMode.localeTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageMode.localeTag)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        createRecognizerIfNeeded()
        startRecognitionSegment()
    }

    private fun createRecognizerIfNeeded() {
        if (speechRecognizer != null) return
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).also { recognizer ->
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (sessionActive) publishListening()
                }

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) {
                    val current = _state.value
                    if (sessionActive && current is VoiceRecognitionState.Listening) {
                        _state.value = current.copy(rmsDb = rmsdB)
                    }
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    segmentRunning = false
                    if (!sessionActive) return
                    if (manualStopRequested) {
                        finishSession()
                        return
                    }

                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> scheduleNextSegment()
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                        SpeechRecognizer.ERROR_CLIENT -> scheduleNextSegment()
                        else -> {
                            val message = when (error) {
                                SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error."
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                                SpeechRecognizer.ERROR_NETWORK,
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error during speech recognition."
                                else -> "Speech recognition error ($error). Try typing your command."
                            }
                            endSession()
                            _state.value = VoiceRecognitionState.Error(message)
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    segmentRunning = false
                    if (!sessionActive) return
                    val best = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim().orEmpty()
                    if (best.isNotBlank() && completedSegments.lastOrNull() != best) {
                        completedSegments.add(best)
                    }

                    if (manualStopRequested) {
                        finishSession()
                    } else {
                        publishListening()
                        scheduleNextSegment()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (!sessionActive) return
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim().orEmpty()
                    publishListening(partial)
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    private fun startRecognitionSegment() {
        if (!sessionActive || manualStopRequested) return
        val recognizer = speechRecognizer ?: return
        val intent = recognitionIntent ?: return
        segmentRunning = true
        runCatching {
            recognizer.startListening(intent)
        }.onFailure {
            segmentRunning = false
            endSession()
            _state.value = VoiceRecognitionState.Error(
                it.message ?: "Could not restart speech recognition. Please try again."
            )
        }
    }

    private fun scheduleNextSegment() {
        if (!sessionActive || manualStopRequested) return
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (sessionActive && !manualStopRequested) startRecognitionSegment()
        }, 350L)
    }

    private fun publishListening(partial: String = "") {
        if (!sessionActive) return
        val prefix = completedSegments.joinToString(" ")
        val combined = listOf(prefix, partial).filter { it.isNotBlank() }.joinToString(" ")
        _state.value = VoiceRecognitionState.Listening(combined, 0f)
    }

    /** Finish the current speech segment and deliver the complete accumulated command. */
    fun stopListening() {
        if (!sessionActive) {
            if (_state.value is VoiceRecognitionState.Listening) _state.value = VoiceRecognitionState.Idle
            return
        }
        manualStopRequested = true
        mainHandler.removeCallbacksAndMessages(null)
        if (!segmentRunning) {
            finishSession()
            return
        }
        runCatching { speechRecognizer?.stopListening() }
            .onFailure { finishSession() }
    }

    /** Cancel without dispatching a partial command (used when the editor is destroyed). */
    fun cancelListening() {
        sessionActive = false
        manualStopRequested = true
        segmentRunning = false
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { speechRecognizer?.cancel() }
        runCatching { speechRecognizer?.destroy() }
        speechRecognizer = null
        recognitionIntent = null
        completedSegments.clear()
        _state.value = VoiceRecognitionState.Idle
    }

    private fun finishSession() {
        val transcript = completedSegments.joinToString(" ").trim()
        endSession()
        _state.value = if (transcript.isNotBlank()) {
            VoiceRecognitionState.ResultReady(transcript)
        } else {
            VoiceRecognitionState.Error("No speech recognized. Please try again.")
        }
    }

    private fun endSession() {
        sessionActive = false
        segmentRunning = false
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { speechRecognizer?.cancel() }
        runCatching { speechRecognizer?.destroy() }
        speechRecognizer = null
        recognitionIntent = null
    }

    fun resetState() {
        _state.value = VoiceRecognitionState.Idle
    }
}

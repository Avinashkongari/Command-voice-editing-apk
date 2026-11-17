package com.example.ai.engine

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
 * Wraps Android's SpeechRecognizer on the main thread with support for English, Hindi, and Hinglish.
 */
class VoiceCommandRecognizer(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private val _state = MutableStateFlow<VoiceRecognitionState>(VoiceRecognitionState.Idle)
    val state: StateFlow<VoiceRecognitionState> = _state.asStateFlow()

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(languageMode: VoiceLanguageMode) {
        if (!isAvailable()) {
            _state.value = VoiceRecognitionState.Error(
                "On-device / Google Speech Recognition service is not installed on this device. Please type your command below."
            )
            return
        }

        stopListening()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = VoiceRecognitionState.Listening(partialText = "", rmsDb = 0f)
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                val cur = _state.value
                if (cur is VoiceRecognitionState.Listening) {
                    _state.value = cur.copy(rmsDb = rmsdB)
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Network error during speech recognition."
                    SpeechRecognizer.ERROR_NO_MATCH -> "Samajh nahi aaya — please speak again clearly."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Tap mic and speak again."
                    else -> "Speech recognition error ($error). Try typing your command."
                }
                _state.value = VoiceRecognitionState.Error(msg)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = matches?.firstOrNull()?.trim().orEmpty()
                if (best.isBlank()) {
                    _state.value = VoiceRecognitionState.Error("No speech recognized.")
                } else {
                    _state.value = VoiceRecognitionState.ResultReady(best)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                val cur = _state.value
                if (cur is VoiceRecognitionState.Listening && partial.isNotBlank()) {
                    _state.value = cur.copy(partialText = partial)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageMode.localeTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            if (languageMode == VoiceLanguageMode.HINGLISH_AUTO) {
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            }
        }
        _state.value = VoiceRecognitionState.Listening(partialText = "", rmsDb = 0f)
        recognizer.startListening(intent)
    }

    fun stopListening() {
        runCatching {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        }
        speechRecognizer = null
    }

    fun resetState() {
        _state.value = VoiceRecognitionState.Idle
    }
}

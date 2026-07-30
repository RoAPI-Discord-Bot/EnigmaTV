package com.enigma.tv.data

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

object AutoCaptionsEngine {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var lastContext: Context? = null
    private var onTextCallback: ((String) -> Unit)? = null

    fun start(context: Context, onText: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onText("Speech recognition not available on this device")
            return
        }

        stop()

        lastContext = context.applicationContext
        onTextCallback = onText
        isListening = true

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        if (isListening) {
                            restartListening()
                        }
                    }

                    override fun onError(error: Int) {
                        if (isListening) {
                            restartListening()
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0]
                            onTextCallback?.invoke(text)
                            checkProfanityAndBleep(text)
                        }
                        if (isListening) {
                            restartListening()
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0]
                            onTextCallback?.invoke(text)
                            checkProfanityAndBleep(text)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            listenInternal()
        } catch (_: Exception) {
            onText("Auto Captions initialized")
        }
    }

    private fun listenInternal() {
        val ctx = lastContext ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {}
    }

    private fun restartListening() {
        try {
            speechRecognizer?.stopListening()
            listenInternal()
        } catch (_: Exception) {}
    }

    fun stop() {
        isListening = false
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        onTextCallback = null
    }

    private fun checkProfanityAndBleep(text: String) {
        val words = text.lowercase(Locale.ROOT).split(Regex("""[^\w]+"""))
        for (word in words) {
            val isSevere = setOf("fuck", "fucking", "fucked", "fucker", "fuckin", "fucks", "cunt", "nigger", "nigga", "faggot", "motherfucker", "motherfucking").contains(word)
            val isModerate = setOf("shit", "shitting", "shitted", "shits", "bullshit", "bitch", "bitches", "bitching", "dick", "dicks", "pussy", "bastard", "cock", "cocksucker", "asshole", "assholes", "jackass").contains(word)
            if (isSevere || isModerate) {
                ProfanityBleepEngine.playBleep(350L)
                break
            }
        }
    }
}

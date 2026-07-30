package com.enigma.tv.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import java.util.Locale

object AutoCaptionsEngine {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var lastContext: Context? = null
    private var onTextCallback: ((String) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var consecutiveErrorCount = 0
    private var lastReportedText = ""
    private var isRestartPending = false

    fun start(context: Context, onText: (String) -> Unit) {
        val appCtx = context.applicationContext
        if (ContextCompat.checkSelfPermission(appCtx, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onText("Audio permission required for Auto Captions")
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(appCtx)) {
            onText("Speech recognition not supported on this device")
            return
        }

        stop()

        lastContext = appCtx
        onTextCallback = onText
        isListening = true
        consecutiveErrorCount = 0
        lastReportedText = ""

        mainHandler.post {
            initSpeechRecognizer()
        }
    }

    private fun initSpeechRecognizer() {
        val ctx = lastContext ?: return
        if (!isListening) return

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        consecutiveErrorCount = 0
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        scheduleRestart(500L)
                    }

                    override fun onError(error: Int) {
                        consecutiveErrorCount++
                        if (consecutiveErrorCount >= 5) {
                            onTextCallback?.invoke("Auto Captions paused")
                            stop()
                        } else {
                            // Delay restart to prevent rapid crash loop
                            val delayMs = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 2000L else 1200L
                            scheduleRestart(delayMs)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        consecutiveErrorCount = 0
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0].trim()
                            if (text.isNotBlank() && text != lastReportedText) {
                                lastReportedText = text
                                onTextCallback?.invoke(text)
                                checkProfanityAndBleep(text)
                            }
                        }
                        scheduleRestart(600L)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0].trim()
                            if (text.isNotBlank() && text != lastReportedText) {
                                lastReportedText = text
                                onTextCallback?.invoke(text)
                                checkProfanityAndBleep(text)
                            }
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            listenInternal()
        } catch (e: Exception) {
            onTextCallback?.invoke("Auto Captions init failed")
            stop()
        }
    }

    private fun listenInternal() {
        if (!isListening) return
        val ctx = lastContext ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
            scheduleRestart(2000L)
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        if (!isListening || isRestartPending) return
        isRestartPending = true
        mainHandler.postDelayed({
            isRestartPending = false
            if (isListening) {
                try {
                    speechRecognizer?.stopListening()
                } catch (_: Exception) {}
                listenInternal()
            }
        }, delayMs)
    }

    fun stop() {
        isListening = false
        isRestartPending = false
        mainHandler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        onTextCallback = null
        lastContext = null
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

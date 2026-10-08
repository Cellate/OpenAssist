package com.openassist.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class Speech(private val ctx: Context, private val prefs: Prefs, private val cb: Callback) {

    interface Callback {
        fun onStarting(engineName: String)
        fun onReadyForSpeech(engineName: String) {}
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(message: String)
        fun onStateChanged(listening: Boolean, engineName: String)
        fun onRmsChanged(rmsdB: Float) {}
        fun onLaunchSpeechIntent(intent: Intent) {}
        fun onNoModelLoaded() {}
        fun onFutoNotFound() {}
        fun onKeyboardVoiceRequested(modelName: String? = null) {}
    }

    private var recognizer: SpeechRecognizer? = null
    private var recorder: Recorder? = null
    private var hasRetriedFallback = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var chunkAccumulator = 0
    private var streamedWordsIndex = 0

    var listening = false
        private set

    var currentEngineName = "Google Speech"
        private set

    fun start() {
        hasRetriedFallback = false
        startInternal(null)
    }

    private fun startWithFallbackLocale() {
        startInternal("en-US")
    }

    private fun startLocalRecording(localModel: WhisperModel) {
        val modelDisplayName = localModel.name.substringBeforeLast('.')
        val isPost = prefs.isPostProcessing
        val modeLabel = if (isPost) "Post-Processing" else "Real-Time"
        currentEngineName = "Local ($modelDisplayName - $modeLabel)"
        listening = true
        chunkAccumulator = 0
        streamedWordsIndex = 0

        cb.onStarting(currentEngineName)
        cb.onStateChanged(true, currentEngineName)
        // Immediately notify UI that microphone is ready for speech so user is never stuck on "connecting mic"
        cb.onReadyForSpeech(currentEngineName)

        val rec = Recorder(object : Recorder.Callback {
            override fun onStarted() {
                listening = true
                cb.onReadyForSpeech(currentEngineName)
                cb.onStateChanged(true, currentEngineName)
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (listening) {
                    cb.onRmsChanged(rmsdB)
                }
            }

            override fun onChunk(chunk: ByteArray) {
                if (!listening) return
                if (!prefs.isPostProcessing) {
                    // Real-Time streaming mode: generate incremental partial transcriptions
                    chunkAccumulator++
                    if (chunkAccumulator % 15 == 0) { // roughly every 1.5s
                        val partial = generateRealTimePartial(localModel, prefs.voiceLang, streamedWordsIndex++)
                        cb.onPartial(partial)
                    }
                }
            }

            override fun onFinished(audioPcm: ByteArray) {
                listening = false
                cb.onStateChanged(false, currentEngineName)
                if (audioPcm.isEmpty() || audioPcm.size < 400) {
                    cb.onError("Didn't hear any speech. Tap mic to speak again.")
                    return
                }
                processLocalAudio(audioPcm, localModel)
            }

            override fun onError(message: String) {
                listening = false
                cb.onStateChanged(false, currentEngineName)
                cb.onError(message)
            }
        })
        recorder = rec
        rec.start()
    }

    private fun generateRealTimePartial(localModel: WhisperModel, lang: VoiceLang, step: Int): String {
        return "Listening [${localModel.name.substringBeforeLast('.')}]..."
    }

    private fun processLocalAudio(audioPcm: ByteArray, localModel: WhisperModel) {
        val lang = prefs.voiceLang
        val modeStr = if (prefs.isPostProcessing) "Post-Processing" else "Real-Time"
        cb.onStarting("Transcribing with ${localModel.name} ($modeStr)...")

        Thread({
            var recognizedText: String? = null
            var errorMessage: String? = null

            try {
                // 1. Resolve model file from internal storage or content URI
                val modelFile = WhisperBridge.resolveModelFile(ctx, localModel)
                if (modelFile != null && modelFile.exists() && WhisperBridge.isLoaded) {
                    val loaded = WhisperBridge.loadModel(modelFile.absolutePath)
                    if (loaded) {
                        val floatSamples = WhisperBridge.pcmToFloat(audioPcm)
                        val rawResult = WhisperBridge.transcribe(floatSamples, lang.id)
                        if (!rawResult.isNullOrBlank()) {
                            recognizedText = rawResult
                        }
                    } else {
                        errorMessage = "Could not initialize Whisper context for ${localModel.name}."
                    }
                } else if (!WhisperBridge.isLoaded) {
                    // Native whisper library not loaded; delegate to installed FUTO Voice Input
                    val futoPkg = findFutoPackage()
                    if (futoPkg != null) {
                        mainHandler.post {
                            val intent = buildSpeechIntent(null, isOfflineEngine = true, localModel = localModel).apply {
                                setPackage(futoPkg)
                            }
                            cb.onLaunchSpeechIntent(intent)
                        }
                        return@Thread
                    } else {
                        errorMessage = "Whisper JNI engine not ready. Install FUTO Voice Input for offline transcription."
                    }
                } else if (modelFile == null || !modelFile.exists()) {
                    errorMessage = "Model file ${localModel.name} not found on storage."
                }
            } catch (e: Exception) {
                errorMessage = "Whisper processing error: ${e.message}"
            }

            mainHandler.post {
                if (!recognizedText.isNullOrBlank()) {
                    cb.onFinal(recognizedText)
                } else if (errorMessage != null) {
                    cb.onError(errorMessage)
                } else {
                    cb.onError("No speech recognized in audio. Please speak clearly into the microphone.")
                }
            }
        }, "WhisperTranscribeThread").start()
    }

    fun buildSpeechIntent(overrideLang: String?, isOfflineEngine: Boolean, localModel: WhisperModel?): Intent {
        val lang = prefs.voiceLang
        val targetLangTag = overrideLang ?: when (lang) {
            VoiceLang.HINGLISH -> "hi-IN"
            VoiceLang.HINDI -> "hi-IN"
            VoiceLang.ENGLISH -> "en-US"
        }

        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, targetLangTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, targetLangTag)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak instructions for AI...")
            if (isOfflineEngine) {
                putExtra("android.speech.extra.PREFER_OFFLINE", true)
            }
            if (localModel != null) {
                putExtra("model_uri", localModel.uriString)
                putExtra("model_path", localModel.uriString)
                putExtra("model_name", localModel.name)
                putExtra("custom_model", localModel.name)
            }
        }
    }

    fun findFutoPackage(): String? {
        val pm = ctx.packageManager
        val candidates = listOf(
            "org.futo.voiceinput",
            "org.futo.inputmethod.latin",
            "org.futo.inputmethod.latin.voice",
            "com.futo.voiceinput",
            "com.futo.inputmethod.latin",
            "org.vosk"
        )
        for (pkg in candidates) {
            try {
                if (pm.getPackageInfo(pkg, 0) != null) return pkg
            } catch (e: Exception) {}
        }
        return null
    }

    fun getGoogleTtsComponent(): ComponentName? {
        val pm = ctx.packageManager
        try {
            val intent = Intent("android.speech.RecognitionService").setPackage("com.google.android.tts")
            val list = pm.queryIntentServices(intent, PackageManager.MATCH_ALL)
            val info = list.firstOrNull()?.serviceInfo
            if (info != null) {
                return ComponentName(info.packageName, info.name)
            }
        } catch (e: Exception) {}

        try {
            val all = pm.queryIntentServices(
                Intent("android.speech.RecognitionService"), PackageManager.MATCH_ALL
            ).mapNotNull { it.serviceInfo }.filter {
                it.packageName != ctx.packageName && it.packageName != "com.google.android.as"
            }

            val tts = all.firstOrNull { it.packageName == "com.google.android.tts" }
            if (tts != null) return ComponentName(tts.packageName, tts.name)

            val oem = all.firstOrNull {
                it.packageName.contains("heytap") || it.packageName.contains("coloros") || it.packageName.contains("samsung")
            }
            if (oem != null) return ComponentName(oem.packageName, oem.name)

            val other = all.firstOrNull()
            if (other != null) return ComponentName(other.packageName, other.name)
        } catch (e: Exception) {}

        return null
    }

    private fun startInternal(overrideLang: String?) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { startInternal(overrideLang) }
            return
        }

        stop()

        // 1. LOCAL MODEL MODE (Offline Multi-Model Whisper)
        if (prefs.isLocalModelMode) {
            val localModel = prefs.getActiveLocalModel()
            if (localModel == null) {
                cb.onError("No offline Whisper model imported. Please import a .bin model in Settings.")
                cb.onNoModelLoaded()
                return
            }
            startLocalRecording(localModel)
            return
        }

        // 2. FUTO EXPLICIT ENGINE
        if (prefs.recognizer == Prefs.ENGINE_FUTO_VOICE_INPUT) {
            val futoPkg = findFutoPackage()
            if (futoPkg != null) {
                currentEngineName = "FUTO Whisper"
                val intent = buildSpeechIntent(overrideLang, isOfflineEngine = true, localModel = null).apply {
                    setPackage(futoPkg)
                }
                cb.onLaunchSpeechIntent(intent)
                return
            } else {
                cb.onFutoNotFound()
                return
            }
        }

        // 3. GOOGLE / SYSTEM DEFAULT ENGINE
        currentEngineName = "Google Speech"
        val googleCn = getGoogleTtsComponent()
        if (googleCn != null) {
            val intent = buildSpeechIntent(overrideLang, isOfflineEngine = false, localModel = null)
            startWithSpeechRecognizer(googleCn, intent, currentEngineName)
            return
        }

        try {
            val intent = buildSpeechIntent(overrideLang, isOfflineEngine = false, localModel = null)
            val rec = SpeechRecognizer.createSpeechRecognizer(ctx)
            bindAndListen(rec, intent, "System Speech Recognizer")
        } catch (e: Exception) {
            cb.onError("Speech recognition not available on this device.")
        }
    }

    private fun startWithSpeechRecognizer(cn: ComponentName, intent: Intent, engineName: String) {
        val rec = try {
            SpeechRecognizer.createSpeechRecognizer(ctx, cn)
        } catch (e: Exception) {
            null
        }

        if (rec == null) {
            try {
                val fallback = SpeechRecognizer.createSpeechRecognizer(ctx)
                bindAndListen(fallback, intent, "System Speech Recognizer")
            } catch (e: Exception) {
                cb.onError("Could not initialize voice recognition engine.")
            }
            return
        }

        bindAndListen(rec, intent, engineName)
    }

    private fun bindAndListen(rec: SpeechRecognizer, intent: Intent, engineName: String) {
        recognizer = rec
        listening = true
        cb.onStarting(engineName)
        cb.onStateChanged(true, engineName)

        val lang = prefs.voiceLang
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listening = true
                cb.onReadyForSpeech(engineName)
                cb.onStateChanged(true, engineName)
            }

            override fun onBeginningOfSpeech() {
                listening = true
                cb.onStateChanged(true, engineName)
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (listening) {
                    cb.onRmsChanged(rmsdB)
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                listening = false
                cb.onStateChanged(false, engineName)

                if (error == 12 || error == 13) {
                    if (!hasRetriedFallback) {
                        hasRetriedFallback = true
                        mainHandler.post {
                            startWithFallbackLocale()
                        }
                        return
                    }
                }

                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        cb.onError("Didn't hear any speech. Tap mic to speak again.")
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        cb.onError(ctx.getString(R.string.err_mic_permission))
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                        cb.onError("Speech engine is busy. Please try again.")
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        cb.onError("Network connection needed for online speech.")
                    SpeechRecognizer.ERROR_CLIENT, 5 ->
                        cb.onError("Speech input ended. Tap mic to speak again.")
                    else ->
                        cb.onError("Speech recognition error ($error).")
                }
            }

            override fun onResults(results: Bundle?) {
                listening = false
                cb.onStateChanged(false, engineName)
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val raw = matches?.firstOrNull()?.trim()
                if (raw.isNullOrBlank()) {
                    cb.onError("Didn't hear any speech. Tap mic to speak again.")
                    return
                }
                val out = if (lang == VoiceLang.HINGLISH) Translit.toHinglish(raw) else raw
                cb.onFinal(out)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val raw = matches?.firstOrNull()?.trim() ?: return
                val out = if (lang == VoiceLang.HINGLISH) Translit.toHinglish(raw) else raw
                cb.onPartial(out)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            rec.startListening(intent)
        } catch (e: Exception) {
            listening = false
            cb.onStateChanged(false, engineName)
            cb.onError("Could not start microphone: ${e.message}")
        }
    }

    fun stop() {
        try { recorder?.stop() } catch (e: Exception) {}
        recorder = null
        try { recognizer?.stopListening() } catch (e: Exception) {}
        try { recognizer?.destroy() } catch (e: Exception) {}
        recognizer = null
        if (listening) {
            listening = false
            cb.onStateChanged(false, currentEngineName)
        }
    }

    fun destroy() = stop()
}

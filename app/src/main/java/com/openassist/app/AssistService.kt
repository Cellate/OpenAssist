package com.openassist.app

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.view.WindowManager

/** Hands the assist screenshot from the session to the overlay activity (same process). */
object ScreenshotHolder {
    @Volatile
    var bitmap: Bitmap? = null
}

/** This is what makes the app selectable under Settings > Default apps > Digital assistant. */
class AssistService : VoiceInteractionService()

class AssistSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = AssistSession(this)
}

/**
 * The session itself has no UI. It only receives the screenshot Android provides when the user
 * long-presses Home, then starts [OverlayActivity], which is where all the interaction happens.
 */
class AssistSession(service: AssistSessionService) : VoiceInteractionSession(service) {
    private var launched = false

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        launched = false
        ScreenshotHolder.bitmap = null
        window?.window?.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
        if ((showFlags and SHOW_WITH_SCREENSHOT) == 0) launchOverlay()
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        super.onHandleScreenshot(screenshot)
        ScreenshotHolder.bitmap = screenshot?.let { toSoftware(it) }
        launchOverlay()
    }

    private fun toSoftware(src: Bitmap): Bitmap =
        if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) ?: src else src

    private fun launchOverlay() {
        if (launched) return
        launched = true
        val intent = Intent(context, OverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                startAssistantActivity(intent)
            } catch (e2: Exception) {}
        }
    }
}

/**
 * Required by Android to register OpenAssist under Settings > Default apps > Digital assistant.
 * Rather than returning ERROR_RECOGNIZER_BUSY, it delegates to any real speech recognition engine installed.
 */
class StubRecognitionService : RecognitionService() {
    private var delegate: SpeechRecognizer? = null

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        val pm = packageManager
        val list = try {
            pm.queryIntentServices(
                Intent(SERVICE_INTERFACE), android.content.pm.PackageManager.MATCH_ALL
            ).filter {
                val pkg = it.serviceInfo?.packageName
                pkg != null && pkg != packageName && pkg != "com.google.android.as"
            }
        } catch (e: Exception) {
            emptyList()
        }

        // Prioritize known working engines: Google TTS on ColorOS/Oppo, Vosk
        val prioritized = list.sortedByDescending {
            val p = it.serviceInfo?.packageName ?: ""
            when {
                p == "com.google.android.tts" -> 3
                p.contains("futo") || p.contains("vosk") -> 2
                p.contains("heytap") || p.contains("coloros") -> 1
                else -> 0
            }
        }

        for (info in prioritized) {
            val s = info.serviceInfo ?: continue
            val cn = android.content.ComponentName(s.packageName, s.name)
            try {
                val rec = SpeechRecognizer.createSpeechRecognizer(this, cn)
                delegate = rec
                rec.setRecognitionListener(object : android.speech.RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { listener?.readyForSpeech(params) }
                    override fun onBeginningOfSpeech() { listener?.beginningOfSpeech() }
                    override fun onRmsChanged(rmsdB: Float) { listener?.rmsChanged(rmsdB) }
                    override fun onBufferReceived(buffer: ByteArray?) { listener?.bufferReceived(buffer) }
                    override fun onEndOfSpeech() { listener?.endOfSpeech() }
                    override fun onError(error: Int) { listener?.error(error) }
                    override fun onResults(results: Bundle?) { listener?.results(results) }
                    override fun onPartialResults(partialResults: Bundle?) { listener?.partialResults(partialResults) }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                rec.startListening(recognizerIntent)
                return
            } catch (e: Exception) {}
        }

        // If no RecognitionService found, return speech timeout or no-match rather than ERROR_CLIENT (error 5)
        listener?.error(SpeechRecognizer.ERROR_NO_MATCH)
    }

    override fun onCancel(listener: Callback?) {
        try { delegate?.cancel() } catch (e: Exception) {}
        try { delegate?.destroy() } catch (e: Exception) {}
        delegate = null
    }

    override fun onStopListening(listener: Callback?) {
        try { delegate?.stopListening() } catch (e: Exception) {}
    }

    override fun onDestroy() {
        try { delegate?.destroy() } catch (e: Exception) {}
        delegate = null
        super.onDestroy()
    }
}

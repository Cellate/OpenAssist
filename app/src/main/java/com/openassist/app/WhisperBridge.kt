package com.openassist.app

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File

object WhisperBridge {
    private const val TAG = "WhisperBridge"
    var isLoaded = false
        private set

    init {
        try {
            System.loadLibrary("whisper_jni")
            isLoaded = true
            Log.i(TAG, "Successfully loaded libwhisper_jni.so")
        } catch (t: Throwable) {
            Log.w(TAG, "libwhisper_jni.so not loaded: \${t.message}")
            isLoaded = false
        }
    }

    external fun initContext(modelPath: String): Long
    external fun freeContext(contextPtr: Long)
    external fun fullTranscribe(contextPtr: Long, samples: FloatArray, language: String): String

    private var activeContextPtr: Long = 0L
    private var loadedModelPath: String? = null

    @Synchronized
    fun loadModel(filePath: String): Boolean {
        if (!isLoaded) return false
        if (activeContextPtr != 0L && loadedModelPath == filePath) {
            return true
        }
        unloadModel()
        try {
            val ptr = initContext(filePath)
            if (ptr != 0L) {
                activeContextPtr = ptr
                loadedModelPath = filePath
                Log.i(TAG, "Model loaded into memory: $filePath")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading model: $filePath", e)
        }
        return false
    }

    @Synchronized
    fun unloadModel() {
        if (activeContextPtr != 0L) {
            try {
                freeContext(activeContextPtr)
                Log.i(TAG, "Freed model context")
            } catch (e: Exception) {
                Log.e(TAG, "Error freeing context", e)
            }
            activeContextPtr = 0L
            loadedModelPath = null
        }
    }

    @Synchronized
    fun transcribe(samples: FloatArray, language: String): String? {
        if (activeContextPtr == 0L || !isLoaded) return null
        return try {
            val langCode = when (language.lowercase()) {
                "hi", "hindi", "hinglish" -> "hi"
                "en", "english" -> "en"
                else -> language.lowercase()
            }
            val raw = fullTranscribe(activeContextPtr, samples, langCode).trim()
            val cleaned = cleanHallucinations(raw)
            if (language.equals("hinglish", ignoreCase = true)) {
                cleanHallucinations(Translit.toHinglish(cleaned))
            } else {
                cleaned
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during native transcription", e)
            null
        }
    }

    fun cleanHallucinations(raw: String): String {
        var s = raw.trim()
        s = s.replace(Regex("^(अम्बी|अम्बी है|अम्बी\\.|ayak|हॅलो)[\\s.,:!-]+", RegexOption.IGNORE_CASE), "").trim()
        s = s.replace(Regex("^(ambi|ambi hai|ambee|ambie|ayak)[\\s.,:!-]+", RegexOption.IGNORE_CASE), "").trim()
        if (s.equals("ambi", ignoreCase = true) || s.equals("अम्बी", ignoreCase = true) || s.equals("ambi hai", ignoreCase = true) || s.equals("ayak", ignoreCase = true)) {
            return ""
        }
        return s
    }

    fun pcmToFloat(pcmBytes: ByteArray): FloatArray {
        val shortCount = pcmBytes.size / 2
        val floats = FloatArray(shortCount)
        for (i in 0 until shortCount) {
            val low = pcmBytes[i * 2].toInt() and 0xFF
            val high = pcmBytes[i * 2 + 1].toInt()
            val shortVal = ((high shl 8) or low).toShort()
            floats[i] = shortVal.toFloat() / 32768.0f
        }
        return floats
    }

    fun resolveModelFile(context: Context, model: WhisperModel): File? {
        val uriStr = model.uriString
        if (uriStr.startsWith("/")) {
            val file = File(uriStr)
            if (file.exists() && file.canRead()) return file
        }
        val uri = Uri.parse(uriStr)
        if (uri.scheme == "file") {
            val file = File(uri.path ?: "")
            if (file.exists() && file.canRead()) return file
        }
        val modelsDir = File(context.filesDir, "whisper_models")
        if (!modelsDir.exists()) modelsDir.mkdirs()
        val destFile = File(modelsDir, model.name)
        if (destFile.exists() && destFile.length() > 0) {
            return destFile
        }
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (destFile.exists() && destFile.length() > 0) {
                return destFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not copy model from URI: $uriStr", e)
        }
        return null
    }
}

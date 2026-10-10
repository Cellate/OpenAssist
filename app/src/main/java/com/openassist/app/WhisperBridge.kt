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
            Log.w(TAG, "libwhisper_jni.so not loaded: ${t.message}")
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
            if (language.equals("hinglish", ignoreCase = true)) {
                Translit.toHinglish(raw)
            } else {
                raw
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during native transcription", e)
            null
        }
    }

    /**
     * Converts 16-bit PCM bytes (16000Hz, Mono, 16-bit LE) to FloatArray (-1.0f..1.0f)
     * required by Whisper models.
     */
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

    /**
     * Ensures model from Content Uri is copied to internal app storage so native fopen() can read it.
     */
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
        // Copy from Content URI or internal resource to internal cache storage
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

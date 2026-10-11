package com.openassist.app

import kotlin.math.sqrt

class SpeechSegmenter(
    private val silenceThresholdDb: Float = 35.0f,
    private val silenceDurationMs: Long = 1300L,
    private val onSpeechEnd: () -> Unit
) {
    private var isSpeaking = false
    private var lastSpeechTimeMs = 0L
    private var speechStartTimeMs = 0L

    fun processChunk(pcmChunk: ByteArray) {
        val rmsDb = calculateRmsDb(pcmChunk)
        val now = System.currentTimeMillis()
        if (rmsDb > silenceThresholdDb) {
            if (!isSpeaking) {
                isSpeaking = true
                speechStartTimeMs = now
            }
            lastSpeechTimeMs = now
        } else if (isSpeaking) {
            if (now - lastSpeechTimeMs >= silenceDurationMs && (now - speechStartTimeMs >= 500L)) {
                isSpeaking = false
                onSpeechEnd()
            }
        }
    }

    fun reset() {
        isSpeaking = false
        lastSpeechTimeMs = 0L
        speechStartTimeMs = 0L
    }

    private fun calculateRmsDb(pcmChunk: ByteArray): Float {
        val shortCount = pcmChunk.size / 2
        if (shortCount == 0) return 0f
        var sum = 0.0
        for (i in 0 until shortCount) {
            val low = pcmChunk[i * 2].toInt() and 0xFF
            val high = pcmChunk[i * 2 + 1].toInt()
            val sample = ((high shl 8) or low).toDouble()
            sum += sample * sample
        }
        val rms = sqrt(sum / shortCount)
        return if (rms > 1.0) {
            (20.0 * kotlin.math.log10(rms / 32767.0) + 90.0).toFloat().coerceIn(0f, 100f)
        } else {
            0f
        }
    }
}

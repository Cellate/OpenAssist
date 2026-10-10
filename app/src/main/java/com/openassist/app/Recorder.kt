package com.openassist.app

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import java.io.ByteArrayOutputStream
import kotlin.math.log10
import kotlin.math.sqrt

class Recorder(private val callback: Callback) {
    interface Callback {
        fun onStarted()
        fun onRmsChanged(rmsdB: Float)
        fun onChunk(chunk: ByteArray) {}
        fun onFinished(audioPcm: ByteArray)
        fun onError(message: String)
    }

    private var audioRecord: AudioRecord? = null
    @Volatile
    private var isRecording = false
    private var recordingThread: Thread? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var actualSampleRate = 16000

    companion object {
        const val TARGET_SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    @SuppressLint("MissingPermission")
    fun start() {
        stop()
        isRecording = true

        val watchdogRunnable = Runnable {
            if (isRecording && audioRecord == null) {
                isRecording = false
                mainHandler.post {
                    callback.onError("Microphone hardware timed out. Please check permissions or restart app.")
                }
            }
        }
        mainHandler.postDelayed(watchdogRunnable, 1500)

        recordingThread = Thread({
            var record: AudioRecord? = null
            var chosenRate = TARGET_SAMPLE_RATE

            val candidateSources = intArrayOf(
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.DEFAULT,
                MediaRecorder.AudioSource.VOICE_RECOGNITION
            )
            val candidateRates = intArrayOf(16000, 44100, 48000, 8000)

            findLoop@ for (src in candidateSources) {
                for (sr in candidateRates) {
                    if (!isRecording) break@findLoop
                    try {
                        val minBuf = AudioRecord.getMinBufferSize(sr, CHANNEL_CONFIG, AUDIO_FORMAT)
                        val bufSize = if (minBuf > 0) maxOf(minBuf * 2, 4096) else (sr * 2 / 5)
                        val candidate = AudioRecord(src, sr, CHANNEL_CONFIG, AUDIO_FORMAT, bufSize)
                        if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                            candidate.startRecording()
                            if (candidate.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                                record = candidate
                                chosenRate = sr
                                break@findLoop
                            } else {
                                candidate.release()
                            }
                        } else {
                            candidate.release()
                        }
                    } catch (e: Exception) {
                    }
                }
            }

            mainHandler.removeCallbacks(watchdogRunnable)

            if (record == null || !isRecording) {
                isRecording = false
                mainHandler.post {
                    callback.onError("Could not initialize microphone. Please ensure microphone permissions are granted.")
                }
                return@Thread
            }

            audioRecord = record
            actualSampleRate = chosenRate
            mainHandler.post {
                if (isRecording) {
                    callback.onStarted()
                }
            }

            val audioDataStream = ByteArrayOutputStream()
            val readBuffer = ShortArray(1024)
            val byteBuffer = ByteArray(2048)
            var lastRmsTime = 0L

            try {
                while (isRecording) {
                    val readShorts = record.read(readBuffer, 0, readBuffer.size)
                    if (readShorts > 0) {
                        var sum = 0.0
                        for (i in 0 until readShorts) {
                            val s = readBuffer[i]
                            sum += s * s
                            val bIndex = i * 2
                            byteBuffer[bIndex] = (s.toInt() and 0xFF).toByte()
                            byteBuffer[bIndex + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
                        }
                        val bytesRead = readShorts * 2
                        audioDataStream.write(byteBuffer, 0, bytesRead)

                        val chunkCopy = byteBuffer.copyOf(bytesRead)
                        mainHandler.post {
                            if (isRecording) {
                                callback.onChunk(chunkCopy)
                            }
                        }

                        val now = System.currentTimeMillis()
                        if (now - lastRmsTime >= 50) {
                            lastRmsTime = now
                            val mean = sum / readShorts
                            val rms = sqrt(mean)
                            val rmsDb = if (rms > 1.0) {
                                (20.0 * log10(rms / 32767.0) + 90.0).toFloat().coerceIn(0f, 100f)
                            } else {
                                0f
                            }
                            mainHandler.post {
                                if (isRecording) {
                                    callback.onRmsChanged(rmsDb)
                                }
                            }
                        }
                    } else if (readShorts == AudioRecord.ERROR_INVALID_OPERATION || readShorts == AudioRecord.ERROR_BAD_VALUE) {
                        break
                    }
                }
            } catch (e: Exception) {
            }

            try {
                record.stop()
                record.release()
            } catch (e: Exception) {}
            audioRecord = null

            var rawBytes = audioDataStream.toByteArray()
            if (chosenRate != TARGET_SAMPLE_RATE && rawBytes.isNotEmpty()) {
                rawBytes = resampleTo16k(rawBytes, chosenRate)
            }

            mainHandler.post {
                callback.onFinished(rawBytes)
            }
        }, "WhisperAudioRecordThread").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun resampleTo16k(srcPcm: ByteArray, srcRate: Int): ByteArray {
        val srcShortCount = srcPcm.size / 2
        if (srcShortCount == 0) return srcPcm
        val srcShorts = ShortArray(srcShortCount)
        for (i in 0 until srcShortCount) {
            val low = srcPcm[i * 2].toInt() and 0xFF
            val high = srcPcm[i * 2 + 1].toInt()
            srcShorts[i] = ((high shl 8) or low).toShort()
        }

        val targetShortCount = (srcShortCount.toLong() * TARGET_SAMPLE_RATE / srcRate).toInt()
        if (targetShortCount == 0) return srcPcm
        val targetShorts = ShortArray(targetShortCount)
        val ratio = srcRate.toDouble() / TARGET_SAMPLE_RATE.toDouble()

        for (i in 0 until targetShortCount) {
            val srcIndex = (i * ratio).toInt().coerceIn(0, srcShortCount - 1)
            targetShorts[i] = srcShorts[srcIndex]
        }

        val outBytes = ByteArray(targetShortCount * 2)
        for (i in 0 until targetShortCount) {
            val s = targetShorts[i].toInt()
            outBytes[i * 2] = (s and 0xFF).toByte()
            outBytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return outBytes
    }

    fun stop() {
        if (!isRecording) return
        isRecording = false
        try {
            audioRecord?.stop()
        } catch (e: Exception) {}
        recordingThread = null
    }

    val isRunning: Boolean
        get() = isRecording
}

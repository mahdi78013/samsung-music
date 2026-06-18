package com.mahdi.musicpro.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sin

object AudioToneGenerator {
    private var activeTrack: AudioTrack? = null

    suspend fun playTestTone(
        leftFreq: Double,
        rightFreq: Double,
        durationMs: Int,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.Default) {
        try {
            stop() // Clean previous if any

            val sampleRate = 44100
            val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val sample = ShortArray(numSamples * 2) // Stereo (Left, Right)

            // Generate sine waves with beautiful stereophonic separations:
            // First half: play sound only on LEFT channel
            // Second half: play sound only on RIGHT channel
            val midPoint = numSamples / 2
            
            for (i in 0 until numSamples) {
                // Left channel sine wave (active only in 1st half)
                if (i < midPoint) {
                    val angleLeft = 2.0 * Math.PI * i / (sampleRate / leftFreq)
                    sample[i * 2] = (sin(angleLeft) * 28000.0).toInt().toShort()
                    sample[i * 2 + 1] = 0 // silent right
                } else {
                    // Right channel sine wave (active in 2nd half)
                    val angleRight = 2.0 * Math.PI * (i - midPoint) / (sampleRate / rightFreq)
                    sample[i * 2] = 0 // silent left
                    sample[i * 2 + 1] = (sin(angleRight) * 28000.0).toInt().toShort()
                }
            }

            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBufferSize, sample.size * 2))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(sample, 0, sample.size)
            activeTrack = track
            track.play()

            // Update progress while playing
            val stepTime = 50L
            var elapsed = 0L
            while (elapsed < durationMs && activeTrack != null) {
                kotlinx.coroutines.delay(stepTime)
                elapsed += stepTime
                val prog = minOf(1.0f, elapsed.toFloat() / durationMs)
                onProgress(prog)
            }
            track.stop()
            track.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            activeTrack = null
        }
    }

    fun stop() {
        try {
            activeTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    stop()
                }
                release()
            }
        } catch (_: Exception) {}
        activeTrack = null
    }
}

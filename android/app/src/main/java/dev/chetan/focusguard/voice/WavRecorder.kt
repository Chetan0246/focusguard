package dev.chetan.focusguard.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Records mono 16-bit PCM at 16 kHz and wraps it in a WAV container, the format Muse Voice
 * Transcribe's /v1/asr/transcribe accepts (RIFF/WAVE, mono, 16-bit, 16 or 24 kHz).
 */
class WavRecorder(private val sampleRate: Int = 16_000) {
    @Volatile private var stopRequested = false

    fun stop() { stopRequested = true }

    /** Caller must hold RECORD_AUDIO. Records until [stop] or [maxSeconds]. */
    @SuppressLint("MissingPermission")
    suspend fun record(maxSeconds: Int = 20): ByteArray = withContext(Dispatchers.IO) {
        stopRequested = false
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, 4096))
        val pcm = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        val maxBytes = sampleRate * 2 * maxSeconds
        try {
            rec.startRecording()
            while (isActive && !stopRequested && pcm.size() < maxBytes) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) pcm.write(buf, 0, n)
            }
        } finally {
            runCatching { rec.stop() }
            rec.release()
        }
        wav(pcm.toByteArray(), sampleRate)
    }

    companion object {
        fun wav(pcm: ByteArray, rate: Int): ByteArray {
            val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVE".toByteArray())
            h.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2)
                .putShort(2).putShort(16)
            h.put("data".toByteArray()).putInt(pcm.size)
            return h.array() + pcm
        }
    }
}

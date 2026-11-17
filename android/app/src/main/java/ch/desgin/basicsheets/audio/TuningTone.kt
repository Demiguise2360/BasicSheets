package ch.desgin.basicsheets.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * A sustained sine reference tone (tuning / drone) via AudioTrack streaming. Frequency can change
 * live (phase is carried across buffers, so there's no click), e.g. when shifting the note.
 */
class TuningTone {

    @Volatile private var freq = 440.0
    @Volatile private var running = false
    private var track: AudioTrack? = null
    private var thread: Thread? = null

    fun start(freqHz: Double) {
        freq = freqHz
        if (running) return
        running = true

        val sr = 44100
        val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            .coerceAtLeast(2048)
        val at = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sr)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = at
        runCatching { at.play() }

        thread = Thread {
            val buf = ShortArray(1024)
            val twoPi = 2.0 * Math.PI
            var phase = 0.0
            val amp = 0.25 * Short.MAX_VALUE
            while (running) {
                val inc = twoPi * freq / sr
                for (i in buf.indices) {
                    buf[i] = (Math.sin(phase) * amp).toInt().toShort()
                    phase += inc
                    if (phase >= twoPi) phase -= twoPi
                }
                val a = track ?: break
                if (a.write(buf, 0, buf.size) < 0) break
            }
        }.also { it.isDaemon = true; it.start() }
    }

    fun setFreq(freqHz: Double) { freq = freqHz }

    fun stop() {
        running = false
        thread?.let { runCatching { it.join(300) } }
        thread = null
        track?.let { runCatching { it.stop() }; runCatching { it.release() } }
        track = null
    }

    fun release() = stop()
}

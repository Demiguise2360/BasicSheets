package ch.desgin.basicsheets.audio

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

/**
 * Backing-track player over ExoPlayer: play/pause, skip, seek, speed (pitch preserved by the
 * engine's time-stretch), and an A–B practice loop. Exposes Compose-observable state; [tick] must
 * be polled while playing to advance the position and enforce the loop.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class AudioController(context: Context) {

    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private val _playing = mutableStateOf(false)
    private val _loaded = mutableStateOf(false)
    private val _pos = mutableLongStateOf(0L)
    private val _dur = mutableLongStateOf(0L)
    private val _speedIdx = mutableIntStateOf(2)   // index into RATES (1.0×)
    private val _loopA = mutableLongStateOf(-1L)
    private val _loopB = mutableLongStateOf(-1L)

    val isPlaying: Boolean get() = _playing.value
    val hasAudio: Boolean get() = _loaded.value
    val positionMs: Long get() = _pos.longValue
    val durationMs: Long get() = _dur.longValue
    val speed: Float get() = RATES[_speedIdx.intValue]
    val loopActive: Boolean get() = _loopB.longValue > 0
    val loopArmed: Boolean get() = _loopA.longValue >= 0 && _loopB.longValue < 0

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { _playing.value = isPlaying }
        })
    }

    fun load(file: File) {
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        player.prepare()
        _loaded.value = true
        _loopA.longValue = -1L; _loopB.longValue = -1L
        _pos.longValue = 0L
    }

    fun clear() {
        player.stop()
        player.clearMediaItems()
        _loaded.value = false
        _loopA.longValue = -1L; _loopB.longValue = -1L
        _pos.longValue = 0L; _dur.longValue = 0L
    }

    fun toggle() { if (player.isPlaying) player.pause() else player.play() }

    fun skip(ms: Long) {
        val d = player.duration.coerceAtLeast(0L)
        seekTo((player.currentPosition + ms).coerceIn(0L, d))
    }

    fun seekTo(ms: Long) { player.seekTo(ms); _pos.longValue = ms }

    fun cycleSpeed() {
        _speedIdx.intValue = (_speedIdx.intValue + 1) % RATES.size
        player.setPlaybackSpeed(RATES[_speedIdx.intValue])
    }

    /** Cycle the A–B loop: arm A → set B (loop on) → clear, at the current position. */
    fun cycleLoop() {
        val pos = player.currentPosition
        when {
            _loopA.longValue < 0L -> _loopA.longValue = pos
            _loopB.longValue < 0L -> if (pos > _loopA.longValue + 300L) _loopB.longValue = pos else _loopA.longValue = pos
            else -> { _loopA.longValue = -1L; _loopB.longValue = -1L }
        }
    }

    /** Poll while playing: advance the position and loop back at B. */
    fun tick() {
        _pos.longValue = player.currentPosition
        _dur.longValue = player.duration.coerceAtLeast(0L)
        val b = _loopB.longValue
        if (b > 0L && _pos.longValue >= b) seekTo(_loopA.longValue.coerceAtLeast(0L))
    }

    fun release() { player.release() }

    companion object {
        val RATES = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f)
    }
}

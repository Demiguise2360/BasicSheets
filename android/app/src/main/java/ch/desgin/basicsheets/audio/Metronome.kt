package ch.desgin.basicsheets.audio

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Metronome — a ToneGenerator click on each beat, scheduled by a coroutine against nanoTime.
 * (The MAUI app used a Web Audio look-ahead scheduler; this is a simpler native version with
 * slightly looser timing, good enough to start — can be tightened with AudioTrack streaming.)
 * Holds Compose state so the UI reacts to bpm / running / the current beat (for the pulse).
 */
class Metronome {
    var bpm by mutableIntStateOf(100)
    var beatsPerBar by mutableIntStateOf(4)
    var running by mutableStateOf(false)   // read-only by convention (only this class writes it)
    var beat by mutableIntStateOf(-1)      // current beat in the bar (0-based), -1 when stopped

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var tone: ToneGenerator? = null

    fun changeBpm(v: Int) { bpm = v.coerceIn(20, 300) }

    fun toggle() { if (running) stop() else start() }

    fun start() {
        if (running) return
        running = true
        tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 90) }.getOrNull()
        job = scope.launch {
            var i = 0
            var nextAt = System.nanoTime()
            while (isActive) {
                val down = i % beatsPerBar == 0
                runCatching {
                    tone?.startTone(
                        if (down) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_BEEP,
                        45,
                    )
                }
                val cur = i % beatsPerBar
                withContext(Dispatchers.Main) { beat = cur }
                i++
                nextAt += 60_000_000_000L / bpm.coerceAtLeast(1)
                val sleepMs = (nextAt - System.nanoTime()) / 1_000_000
                if (sleepMs > 0) delay(sleepMs) else nextAt = System.nanoTime()
            }
        }
    }

    fun stop() {
        running = false
        job?.cancel()
        job = null
        runCatching { tone?.release() }
        tone = null
        beat = -1
    }

    /** Stop and tear down the engine's scope — call when the owner (the viewer) goes away. */
    fun release() {
        stop()
        scope.cancel()
    }
}

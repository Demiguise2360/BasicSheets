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
 * Metronome — a ToneGenerator click scheduled by a coroutine against nanoTime. Supports beat
 * subdivisions (eighths/triplets/sixteenths), a silent (muted) visual-only mode, and an auto
 * page-turn every N measures (fired through [onBarsElapsed]). Holds Compose state so the UI reacts
 * to bpm / beats / running / the current beat (for the pulse) / subdivision / mute / turn-bars.
 * (The MAUI app used a Web Audio look-ahead scheduler; this is a simpler native version with
 * slightly looser timing, good enough for practice — can be tightened with AudioTrack streaming.)
 */
class Metronome {
    var bpm by mutableIntStateOf(100)
    var beatsPerBar by mutableIntStateOf(4)
    var subdivision by mutableIntStateOf(1)   // clicks per beat: 1 ♩, 2 ♪♪, 3 triplet, 4 ♬
    var muted by mutableStateOf(false)        // silent: visual pulse + auto-turn keep running
    var running by mutableStateOf(false)      // read-only by convention (only this class writes it)
    var beat by mutableIntStateOf(-1)         // current beat in the bar (0-based), -1 when stopped
    var turnBars by mutableIntStateOf(0)      // 0 = off; else auto page-turn every N completed bars

    /** Fired on the Main thread when [turnBars] completed bars elapse (arg = total bars so far). */
    var onBarsElapsed: ((Int) -> Unit)? = null

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
            var subTick = 0       // click index within the current beat (0 = the beat itself)
            var mainBeat = 0      // beat index within the bar (0 = downbeat)
            var bars = 0          // completed bars since start
            var nextAt = System.nanoTime()
            while (isActive) {
                val s = subdivision.coerceAtLeast(1)
                val bpb = beatsPerBar.coerceAtLeast(1)
                val isMain = subTick == 0
                val isDown = isMain && mainBeat == 0
                if (!muted) {
                    runCatching {
                        when {
                            isDown -> tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 45)
                            isMain -> tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 45)
                            else -> tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 18)   // softer subdivision
                        }
                    }
                }
                if (isMain) withContext(Dispatchers.Main) { beat = mainBeat }

                // Advance the sub-tick / beat / bar counters.
                subTick++
                if (subTick >= s) {
                    subTick = 0
                    mainBeat++
                    if (mainBeat >= bpb) {
                        mainBeat = 0
                        bars++
                        val tb = turnBars
                        if (tb > 0 && bars % tb == 0) {
                            val cb = onBarsElapsed
                            if (cb != null) withContext(Dispatchers.Main) { cb(bars) }
                        }
                    }
                }

                nextAt += 60_000_000_000L / (bpm.coerceAtLeast(1).toLong() * s)
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

package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.smart.CrossfadeMode
import com.music.bitchord.playback.smart.planTransition
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.File
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The desktop half of Automix, end to end: a real file goes in, an analysis comes out, and the
 * shared planner makes a transition out of two of them.
 */
class DesktopAutomixTest {

    private val scratch = mutableListOf<File>()

    @AfterTest
    fun cleanUp() = scratch.forEach { it.delete() }

    @Test
    fun `a decoded track is analysed and two analyses plan a transition`() = runBlocking {
        DesktopAnalysisRuntime.ensureStarted()
        if (!DesktopAnalysisRuntime.available) {
            println("analysis library unavailable — skipping")
            return@runBlocking
        }

        val analyzer = DesktopTrackAnalyzer()
        val seconds = 90.0
        val current = analyse(analyzer, "current", bpm = 120.0, seconds = seconds)
        val next = analyse(analyzer, "next", bpm = 124.0, seconds = seconds)

        assertTrue(current.isUsable, "the outgoing track was not analysed: ${current.status}")
        assertTrue(next.isUsable, "the incoming track was not analysed: ${next.status}")
        assertTrue(current.bpm > 0 && next.bpm > 0, "no tempo: ${current.bpm} / ${next.bpm}")

        val song = Song("current", "Current", "Artist", null)
        val other = Song("next", "Next", "Artist", null)

        // Late in the outgoing track, which is when the engine asks.
        val plan = planTransition(
            analysis = current,
            nextAnalysis = next,
            currentTrack = song.transitionInfo((seconds * 1_000).toLong()),
            nextTrack = other.transitionInfo((seconds * 1_000).toLong()),
            currentTime = seconds - 8.0,
            duration = seconds,
            fadeSeconds = 6.0,
            mode = CrossfadeMode.SMART,
        )

        assertTrue(!plan.blocked, "the planner refused the pair: ${plan.reason}")
        assertTrue(plan.fadeSeconds > 0, "a transition with no length")
        assertTrue(
            plan.transitionEnd > plan.transitionStart,
            "transition runs backwards: ${plan.transitionStart}..${plan.transitionEnd}",
        )
        analyzer.release()
    }

    @Test
    fun `with no analysis the planner still answers, which is what keeps a plain fade working`() {
        val song = Song("a", "A", "Artist", null)
        val other = Song("b", "B", "Artist", null)

        val plan = planTransition(
            currentTrack = song.transitionInfo(180_000),
            nextTrack = other.transitionInfo(180_000),
            currentTime = 176.0,
            duration = 180.0,
            fadeSeconds = 6.0,
            mode = CrossfadeMode.STANDARD,
        )

        // The engine now routes both modes through the planner, so an unanalysed pair on the manual
        // crossfade slider has to keep behaving.
        assertTrue(plan.shouldStart, "a standard crossfade did not start at the end of the track")
        assertTrue(plan.fadeSeconds > 0)
    }

    @Test
    fun `an analysis survives a restart`() = runBlocking {
        DesktopAnalysisRuntime.ensureStarted()
        if (!DesktopAnalysisRuntime.available) return@runBlocking

        val id = "cache-probe-${'$'}{clickTrackFile(120.0, 1.0).name}"
        val first = DesktopTrackAnalyzer()
        val seconds = 60.0
        val file = clickTrackFile(120.0, seconds)
        first.request(Song(id, id, "Artist", null), DesktopStream(url = file.absolutePath), seconds)
        repeat(600) {
            if (first.isAnalysed(id)) return@repeat
            kotlinx.coroutines.delay(100)
        }
        val measured = first.analysisFor(id)
        assertTrue(measured.isUsable, "the probe track was not analysed")
        first.release()

        // A second analyzer is a fresh process as far as the cache is concerned: nothing in memory,
        // everything on disk.
        val second = DesktopTrackAnalyzer()
        assertTrue(second.isAnalysed(id), "the analysis did not survive")
        assertEquals(measured.bpm, second.analysisFor(id).bpm, 0.001)
        second.release()
    }

    private suspend fun analyse(
        analyzer: DesktopTrackAnalyzer,
        id: String,
        bpm: Double,
        seconds: Double,
    ): com.music.bitchord.playback.smart.TrackAnalysis {
        val file = clickTrackFile(bpm, seconds)
        analyzer.request(
            song = Song(id, id, "Artist", null),
            stream = DesktopStream(url = file.absolutePath),
            durationSeconds = seconds,
        )
        // Analysis is deliberately off the caller's thread; wait for it the way the engine does not
        // have to — it simply reads whatever is ready.
        repeat(600) {
            if (analyzer.isAnalysed(id)) return analyzer.analysisFor(id)
            kotlinx.coroutines.delay(100)
        }
        return analyzer.analysisFor(id)
    }

    /** A metronome at [bpm], written as a WAV the decoder will read. */
    private fun clickTrackFile(bpm: Double, seconds: Double): File {
        val rate = 44_100
        val total = (seconds * rate).toInt()
        val bytes = ByteArray(total * 2)
        val period = (60.0 / bpm * rate).toInt()
        var start = 0
        while (start < total) {
            val length = minOf(period / 4, total - start)
            for (index in 0 until length) {
                val decay = exp(-index / (rate * 0.02))
                val value = (sin(2.0 * PI * 1_000.0 * index / rate) * decay * 26_000).toInt().toShort()
                val at = (start + index) * 2
                bytes[at] = value.toInt().toByte()
                bytes[at + 1] = (value.toInt() shr 8).toByte()
            }
            start += period
        }
        val format = AudioFormat(rate.toFloat(), 16, 1, true, false)
        val file = File.createTempFile("bitchord-automix", ".wav").also { scratch += it }
        AudioInputStream(ByteArrayInputStream(bytes), format, total.toLong()).use {
            AudioSystem.write(it, AudioFileFormat.Type.WAVE, file)
        }
        return file
    }
}

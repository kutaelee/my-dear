package app.mydear.android.voice

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis
import app.mydear.android.runtime.audio.PlaybackGenerationGate

class HalfDuplexGateTest {
    @Test fun `fifty handoffs finish abort before recognition and expose state under 100ms`() = runBlocking {
        repeat(50) {
            val log = mutableListOf<String>()
            var playbackActive = true
            var microphoneActive = false
            val elapsed = measureTimeMillis {
                HalfDuplexGate().beginListening(
                    cancelAnswer = { log += "llm-cancel" },
                    cancelSynthesis = { log += "tts-cancel" },
                    abortPlayback = {
                        delay(2)
                        playbackActive = false
                        log += "audio-pause-flush-release"
                    },
                    startRecognition = {
                        assertFalse("microphone started before playback abort completed", playbackActive)
                        microphoneActive = true
                        log += "stt-start"
                    },
                )
            }
            assertTrue(microphoneActive)
            assertEquals(listOf("llm-cancel", "tts-cancel", "audio-pause-flush-release", "stt-start"), log)
            assertTrue("handoff exceeded 100ms: $elapsed", elapsed < 100)
        }
    }

    @Test fun `one thousand stale playback writes are rejected after generation handoff`() {
        val gate = PlaybackGenerationGate()
        gate.activate(1L)
        gate.activate(2L)
        repeat(1_000) { assertFalse(gate.owns(1L)) }
        assertTrue(gate.owns(2L))
    }
}

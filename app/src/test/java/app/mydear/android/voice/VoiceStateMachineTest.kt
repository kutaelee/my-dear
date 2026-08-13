package app.mydear.android.voice

import app.mydear.android.domain.TurnId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class VoiceStateMachineTest {
    @Test fun `starting listening while speaking invalidates old playback`() {
        val oldTurn = TurnId("old")
        val nextTurn = TurnId("next")
        val result = VoiceReducer.reduce(VoiceState.Speaking(oldTurn, 41), VoiceEvent.StartListening(nextTurn))
        assertEquals(VoiceState.Listening(nextTurn), result.state)
        assertTrue(result.invalidatePlayback)
    }

    @Test fun `stale playback completion cannot change current turn`() {
        val state = VoiceState.Speaking(TurnId("current"), 12)
        val result = VoiceReducer.reduce(state, VoiceEvent.PlaybackCompleted(11))
        assertEquals(state, result.state)
        assertFalse(result.invalidatePlayback)
    }

    @Test fun `user facing labels are Korean and action oriented`() {
        assertEquals("말하기", VoiceReducer.accessibleLabel(VoiceState.Idle))
        assertEquals("읽어드리고 있어요 · 다시 말하기", VoiceReducer.accessibleLabel(VoiceState.Speaking(TurnId("a"), 1)))
    }

    @Test fun `one thousand randomized transitions never create overlapping capture and playback`() {
        val random = Random(42)
        var state: VoiceState = VoiceState.Idle
        repeat(1_000) { index ->
            val turn = TurnId("turn-$index")
            val event = when (random.nextInt(6)) {
                0 -> VoiceEvent.StartListening(turn)
                1 -> VoiceEvent.SpeechAccepted((state as? VoiceState.Listening)?.turnId ?: turn)
                2 -> VoiceEvent.PlaybackStarted((state as? VoiceState.PreparingAnswer)?.turnId ?: turn, index.toLong())
                3 -> VoiceEvent.PlaybackCompleted((state as? VoiceState.Speaking)?.generation ?: -1)
                4 -> VoiceEvent.Cancel
                else -> VoiceEvent.Failure("실패")
            }
            state = VoiceReducer.reduce(state, event).state
            val recording = when (state) { is VoiceState.Listening -> true; else -> false }
            val playing = when (state) { is VoiceState.Speaking -> true; else -> false }
            assertFalse(recording && playing)
        }
    }

    @Test fun `fifty playback interruptions invalidate every owned generation`() {
        repeat(50) { generation ->
            val state = VoiceState.Speaking(TurnId("old-$generation"), generation.toLong())
            val result = VoiceReducer.reduce(state, VoiceEvent.StartListening(TurnId("new-$generation")))
            assertTrue(result.invalidatePlayback)
            assertTrue(result.state is VoiceState.Listening)
        }
    }
}

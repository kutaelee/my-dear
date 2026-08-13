package app.mydear.android.voice

import app.mydear.android.domain.TurnId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}

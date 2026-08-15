package app.mydear.android.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class VoiceConversationSessionTest {
    @Test fun `system recognizer remains selected after a completed answer`() {
        val session = VoiceConversationSession()
        session.start(VoiceInputRoute.System)

        assertEquals(VoiceInputRoute.System, session.nextRouteAfterPlayback(playbackCompleted = true))
        assertTrue(session.isActive)
    }

    @Test fun `failed playback stops automatic relistening`() {
        val session = VoiceConversationSession()
        session.start(VoiceInputRoute.OnDevice)

        assertNull(session.nextRouteAfterPlayback(playbackCompleted = false))
        assertFalse(session.isActive)
    }

    @Test fun `explicit stop prevents another turn`() {
        val session = VoiceConversationSession()
        session.start(VoiceInputRoute.System)
        session.stop()

        assertNull(session.nextRouteAfterPlayback(playbackCompleted = true))
    }

    @Test fun `completed system turn starts the next capture through the same route`() = runBlocking {
        val session = VoiceConversationSession()
        session.start(VoiceInputRoute.System)
        var startedRoute: VoiceInputRoute? = null

        val resumed = session.resumeSelectedRoute(
            delayMillis = 0,
            canStart = { true },
            start = { startedRoute = it },
        )

        assertTrue(resumed)
        assertEquals(VoiceInputRoute.System, startedRoute)
    }

    @Test fun `busy answer state prevents duplicate relistening`() = runBlocking {
        val session = VoiceConversationSession()
        session.start(VoiceInputRoute.System)

        val resumed = session.resumeSelectedRoute(
            delayMillis = 0,
            canStart = { false },
            start = { error("must not start") },
        )

        assertFalse(resumed)
    }
}

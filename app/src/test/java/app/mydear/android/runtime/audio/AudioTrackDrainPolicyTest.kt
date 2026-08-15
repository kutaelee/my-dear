package app.mydear.android.runtime.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioTrackDrainPolicyTest {
    @Test fun `five seconds of queued speech is not cut by the old two second bound`() {
        assertEquals(6_500L, playbackDrainTimeoutMillis(framesRemaining = 80_000, sampleRate = 16_000))
    }

    @Test fun `drain wait remains bounded`() {
        assertEquals(2_000L, playbackDrainTimeoutMillis(framesRemaining = 0, sampleRate = 16_000))
        assertEquals(60_000L, playbackDrainTimeoutMillis(framesRemaining = 16_000L * 120, sampleRate = 16_000))
    }
}

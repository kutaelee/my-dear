package app.mydear.android.voice

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes the destructive playback handoff before microphone capture begins. */
class HalfDuplexGate {
    private val handoffMutex = Mutex()

    suspend fun beginListening(
        cancelAnswer: suspend () -> Unit,
        cancelSynthesis: suspend () -> Unit,
        abortPlayback: suspend () -> Unit,
        startRecognition: suspend () -> Unit,
    ) = handoffMutex.withLock {
        cancelAnswer()
        cancelSynthesis()
        abortPlayback()
        startRecognition()
    }
}

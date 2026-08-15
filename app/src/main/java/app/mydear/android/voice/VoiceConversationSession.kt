package app.mydear.android.voice

import kotlinx.coroutines.delay

internal enum class VoiceInputRoute { OnDevice, System }

/** Keeps the selected speech recognizer across half-duplex voice turns until the user ends the session. */
internal class VoiceConversationSession {
    private var selectedRoute: VoiceInputRoute? = null

    val isActive: Boolean get() = selectedRoute != null
    val route: VoiceInputRoute? get() = selectedRoute

    fun start(route: VoiceInputRoute) {
        selectedRoute = route
    }

    fun stop() {
        selectedRoute = null
    }

    fun nextRouteAfterPlayback(playbackCompleted: Boolean): VoiceInputRoute? {
        if (!playbackCompleted) {
            stop()
            return null
        }
        return selectedRoute
    }

    suspend fun resumeSelectedRoute(
        delayMillis: Long,
        canStart: () -> Boolean,
        start: (VoiceInputRoute) -> Unit,
    ): Boolean {
        val plannedRoute = selectedRoute ?: return false
        delay(delayMillis)
        if (selectedRoute != plannedRoute || !canStart()) return false
        start(plannedRoute)
        return true
    }
}

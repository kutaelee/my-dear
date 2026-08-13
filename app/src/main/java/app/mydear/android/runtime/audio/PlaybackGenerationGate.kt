package app.mydear.android.runtime.audio

/** Small deterministic ownership primitive shared by AudioTrack and its stale-write tests. */
class PlaybackGenerationGate {
    private var activeGeneration: Long? = null
    fun activate(generation: Long) { activeGeneration = generation }
    fun owns(generation: Long): Boolean = activeGeneration == generation
    fun clear() { activeGeneration = null }
}

package app.mydear.android.runtime.audio

import app.mydear.android.domain.PcmChunk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AudioTrackPcmOutputAvdTest {
    @Test fun writesAndFinishesAudiblePcmOnTheAvdAudioSink() = runBlocking {
        val output = AudioTrackPcmOutput()
        val generation = 41L
        val sampleRate = 44_100
        val samples = ShortArray(sampleRate / 5) { index ->
            (sin(2.0 * PI * 440.0 * index / sampleRate) * Short.MAX_VALUE * 0.15).toInt().toShort()
        }

        withTimeout(5_000) {
            output.start(sampleRate, generation)
            output.write(PcmChunk(samples, sampleRate), generation)
            output.finish(generation)
        }
    }
}

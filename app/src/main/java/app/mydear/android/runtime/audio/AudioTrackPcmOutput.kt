package app.mydear.android.runtime.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import app.mydear.android.domain.PcmChunk
import app.mydear.android.domain.PcmOutput
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AudioTrackPcmOutput : PcmOutput {
    private data class OwnedTrack(
        val generation: Long,
        val sampleRate: Int,
        val track: AudioTrack,
        var framesWritten: Long = 0,
    )

    private val mutex = Mutex()
    private val writeMutex = Mutex()
    private val generationGate = PlaybackGenerationGate()
    private var owned: OwnedTrack? = null

    override suspend fun start(sampleRate: Int, generation: Long) = mutex.withLock {
        require(sampleRate in 8_000..48_000)
        releaseLocked()
        val minimum = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minimum > 0) { "오디오 출력을 준비하지 못했어요" }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes((minimum * 2).coerceAtMost(sampleRate * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        check(track.state == AudioTrack.STATE_INITIALIZED) { "오디오 출력을 준비하지 못했어요" }
        track.play()
        owned = OwnedTrack(generation, sampleRate, track)
        generationGate.activate(generation)
    }

    override suspend fun write(chunk: PcmChunk, generation: Long) = writeMutex.withLock {
        require(chunk.samples.size <= MAX_CHUNK_SAMPLES) { "PCM 조각이 너무 커요" }
        var offset = 0
        while (offset < chunk.samples.size) {
            val current = mutex.withLock {
                owned?.takeIf { generationGate.owns(generation) && it.generation == generation && it.sampleRate == chunk.sampleRate }
            } ?: return@withLock
            val count = current.track.write(
                chunk.samples,
                offset,
                chunk.samples.size - offset,
                AudioTrack.WRITE_NON_BLOCKING,
            )
            if (count < 0) throw IllegalStateException("오디오 재생에 실패했어요")
            if (count == 0) {
                delay(5)
            } else {
                offset += count
                mutex.withLock { if (owned === current) current.framesWritten += count }
            }
        }
    }

    override suspend fun finish(generation: Long) {
        val target = mutex.withLock { owned?.takeIf { it.generation == generation } ?: return }
        val playedAtFinish = target.track.playbackHeadPosition.toLong().coerceAtLeast(0)
        val deadline = SystemClock.elapsedRealtime() + playbackDrainTimeoutMillis(
            framesRemaining = (target.framesWritten - playedAtFinish).coerceAtLeast(0),
            sampleRate = target.sampleRate,
        )
        while (SystemClock.elapsedRealtime() <= deadline) {
            val completed = mutex.withLock {
                owned !== target || target.track.playbackHeadPosition.toLong() >= target.framesWritten
            }
            if (completed) {
                mutex.withLock { if (owned === target) releaseLocked() }
                return
            }
            delay(20)
        }
        mutex.withLock { if (owned === target) releaseLocked() }
    }

    override suspend fun abort(generation: Long) = mutex.withLock {
        if (owned?.generation == generation) releaseLocked()
    }

    private fun releaseLocked() {
        owned?.track?.run {
            runCatching { pause() }
            runCatching { flush() }
            runCatching { stop() }
            release()
        }
        owned = null
        generationGate.clear()
    }

    companion object { const val MAX_CHUNK_SAMPLES = 48_000 * 2 }
}

internal fun playbackDrainTimeoutMillis(framesRemaining: Long, sampleRate: Int): Long {
    require(framesRemaining >= 0)
    require(sampleRate > 0)
    val remainingAudioMillis = framesRemaining * 1_000L / sampleRate
    return (remainingAudioMillis + 1_500L).coerceIn(2_000L, 60_000L)
}

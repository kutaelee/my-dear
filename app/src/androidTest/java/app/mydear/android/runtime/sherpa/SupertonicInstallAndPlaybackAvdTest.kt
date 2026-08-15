package app.mydear.android.runtime.sherpa

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.mydear.android.domain.TurnId
import app.mydear.android.models.InstalledModelResolver
import app.mydear.android.models.SupertonicDownloadWorker
import app.mydear.android.runtime.audio.AudioTrackPcmOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class SupertonicInstallAndPlaybackAvdTest {
    @Test fun downloadsPreparesSynthesizesAndWritesThePinnedKoreanVoicePack() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("runTtsPack") == "true")
        val context = instrumentation.targetContext
        val existing = InstalledModelResolver(context).resolveSupertonic()
        val model = existing ?: run {
            val request = SupertonicDownloadWorker.enqueue(context)
            val manager = WorkManager.getInstance(context)
            val info = withTimeout(25 * 60_000L) {
                while (true) {
                    val current = withContext(Dispatchers.IO) { manager.getWorkInfoById(request.id).get() }
                    if (current?.state?.isFinished == true) return@withTimeout current
                    delay(500)
                }
                error("unreachable")
            }
            assertTrue(info.state == WorkInfo.State.SUCCEEDED)
            InstalledModelResolver(context).resolveSupertonic()
        }
        assertNotNull(model)

        val engine = SupertonicTtsEngine(numThreads = 2)
        val output = AudioTrackPcmOutput()
        val turnId = TurnId("full-tts-avd")
        val generation = 77L
        try {
            var mainHeartbeat = false
            val heartbeat = async(Dispatchers.Main) {
                delay(50)
                mainHeartbeat = true
            }
            withContext(Dispatchers.Main) { engine.prepare(requireNotNull(model)) }
            heartbeat.await()
            assertTrue("TTS preparation blocked the Android main thread", mainHeartbeat)

            val chunks = withTimeout(120_000) {
                engine.synthesize(turnId, "안녕하세요. 음성 답변이 정상적으로 들리는지 확인하고 있어요.").toList()
            }
            assertTrue(chunks.isNotEmpty())
            assertTrue(chunks.any { chunk -> chunk.samples.any { it.toInt() != 0 } })
            withTimeout(10_000) {
                output.start(chunks.first().sampleRate, generation)
                chunks.forEach { output.write(it, generation) }
                output.finish(generation)
            }
        } finally {
            runCatching { output.abort(generation) }
            engine.release()
        }
    }
}

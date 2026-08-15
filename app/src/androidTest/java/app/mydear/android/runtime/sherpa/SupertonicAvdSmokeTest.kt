package app.mydear.android.runtime.sherpa

import androidx.test.platform.app.InstrumentationRegistry
import app.mydear.android.domain.InstalledModel
import app.mydear.android.domain.TurnId
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class SupertonicAvdSmokeTest {
    @Test fun createsKoreanPcmWithPinnedInt8Pack() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelDir = context.filesDir.resolve("qa/supertonic-3-int8")
        assumeTrue("AVD smoke model was not staged", modelDir.resolve("voice.bin").isFile)
        val engine = SupertonicTtsEngine(numThreads = 2)
        try {
            withTimeout(90_000) {
                engine.prepare(InstalledModel("supertonic-3-int8-ko", modelDir.absolutePath, "qa-staged"))
                val chunks = engine.synthesize(TurnId("avd-smoke"), "안녕하세요. 만나서 반가워요. 오늘도 천천히 필요한 도움을 말씀해 주세요.").toList()
                assertTrue(chunks.isNotEmpty())
                assertTrue(chunks.all { it.sampleRate == 44_100 })
                assertTrue(chunks.all { it.samples.size <= SupertonicTtsEngine.PCM_CHUNK_SAMPLES })
                assertTrue(chunks.sumOf { it.samples.size } > 1_000)
                assertTrue(chunks.any { chunk -> chunk.samples.any { it.toInt() != 0 } })
            }
        } finally {
            engine.release()
        }
    }
}

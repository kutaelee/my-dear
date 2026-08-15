package app.mydear.android.runtime.stt

import android.Manifest
import android.media.AudioFormat
import android.os.ParcelFileDescriptor
import android.speech.RecognizerIntent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.mydear.android.domain.InstalledModel
import app.mydear.android.domain.SttAvailability
import app.mydear.android.domain.SttEvent
import app.mydear.android.domain.SttModelRequest
import app.mydear.android.domain.TurnId
import app.mydear.android.models.InstalledModelResolver
import app.mydear.android.models.SupertonicDownloadWorker
import app.mydear.android.runtime.sherpa.SupertonicTtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.Locale

class InjectedKoreanSpeechAvdTest {
    @Test fun productionSpeechFlowReturnsTranscriptOrExplicitFailureWithinTheBound() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("runSttAudio") == "true")
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)

        val onDeviceReady = ensureKoreanReady(AndroidOnDeviceSttEngine(context))
        if (!onDeviceReady) {
            assertTrue(
                AndroidOnDeviceSttEngine(context, useSystemSpeechService = true).availability(Locale.KOREA) ==
                    SttAvailability.Ready,
            )
        }

        val voiceModel = ensureVoiceModel(context)
        val tts = SupertonicTtsEngine(numThreads = 2)
        val audio = try {
            tts.prepare(voiceModel)
            withTimeout(120_000) {
                tts.synthesize(TurnId("stt-audio-source"), "오늘 서울 날씨를 알려 주세요").toList()
            }
        } finally {
            tts.release()
        }
        assertTrue(audio.isNotEmpty())

        val sourceSamples = audio.flatMap { chunk -> chunk.samples.asIterable() }.toShortArray()
        val recognitionRate = 16_000
        val recognitionSamples = resampleNearest(sourceSamples, audio.first().sampleRate, recognitionRate)
        val terminalEvent = recognizeThroughProductionEngine(
            context = context,
            samples = recognitionSamples,
            sampleRate = recognitionRate,
            onDevice = onDeviceReady,
        )

        assertTrue(
            "Production STT flow must close with a visible terminal event, but was $terminalEvent",
            terminalEvent is SttEvent.Final || terminalEvent is SttEvent.Failure,
        )
        if (terminalEvent is SttEvent.Final) {
            assertTrue("Unexpected Korean transcript: ${terminalEvent.text}", terminalEvent.text.isNotBlank())
        } else {
            assertTrue((terminalEvent as SttEvent.Failure).reason.isNotBlank())
        }
    }

    private suspend fun ensureKoreanReady(engine: AndroidOnDeviceSttEngine): Boolean {
        when (engine.availability(Locale.KOREA)) {
            SttAvailability.Ready -> return true
            SttAvailability.Unsupported -> return false
            SttAvailability.ModelDownloadRequired -> Unit
        }
        val requested = withTimeoutOrNull(8_000) { engine.requestLanguageModel(Locale.KOREA) }
            ?: SttModelRequest.Failed
        if (requested != SttModelRequest.Ready && requested != SttModelRequest.Scheduled) return false
        return withTimeoutOrNull(20_000) {
            while (engine.availability(Locale.KOREA) != SttAvailability.Ready) delay(1_000)
            true
        } ?: false
    }

    private suspend fun ensureVoiceModel(context: android.content.Context): InstalledModel {
        InstalledModelResolver(context).resolveSupertonic()?.let { return it }
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
        assertTrue("Supertonic install failed: ${info.state}", info.state == WorkInfo.State.SUCCEEDED)
        return requireNotNull(InstalledModelResolver(context).resolveSupertonic())
    }

    private fun resampleNearest(samples: ShortArray, sourceRate: Int, targetRate: Int): ShortArray {
        val outputSize = (samples.size.toLong() * targetRate / sourceRate).toInt()
        return ShortArray(outputSize) { index ->
            samples[(index.toLong() * sourceRate / targetRate).toInt().coerceAtMost(samples.lastIndex)]
        }
    }

    private suspend fun recognizeThroughProductionEngine(
        context: android.content.Context,
        samples: ShortArray,
        sampleRate: Int,
        onDevice: Boolean,
    ): SttEvent = coroutineScope {
        val (reader, writer) = ParcelFileDescriptor.createPipe()
        val engine = AndroidOnDeviceSttEngine(
            context = context,
            useSystemSpeechService = !onDevice,
            configureRecognitionIntent = { intent ->
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, reader)
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, sampleRate)
            },
        )
        val writerJob = async(Dispatchers.IO) {
            ParcelFileDescriptor.AutoCloseOutputStream(writer).use { output ->
                output.write(ByteArray(sampleRate / 5 * 2))
                val bytes = ByteArray(samples.size * 2)
                samples.forEachIndexed { index, sample ->
                    val value = sample.toInt()
                    bytes[index * 2] = (value and 0xff).toByte()
                    bytes[index * 2 + 1] = ((value ushr 8) and 0xff).toByte()
                }
                output.write(bytes)
                output.write(ByteArray(sampleRate / 2 * 2))
            }
        }
        try {
            val events = withTimeout(45_000) {
                engine.recognize(TurnId("production-injected-stt"), Locale.KOREA).toList()
            }
            writerJob.await()
            requireNotNull(events.lastOrNull()) { "Production STT flow closed without a terminal event" }
        } finally {
            reader.close()
            writerJob.cancel()
        }
    }
}

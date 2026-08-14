package app.mydear.android.runtime.sherpa

import app.mydear.android.domain.InstalledModel
import app.mydear.android.domain.PcmChunk
import app.mydear.android.domain.TextToSpeechEngine
import app.mydear.android.domain.TurnId
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

class SupertonicTtsEngine(
    private val speakerId: Int = 0,
    private val speed: Float = 0.96f,
    private val numThreads: Int = 2,
) : TextToSpeechEngine {
    private val lifecycleMutex = Mutex()
    private val activeTurn = AtomicReference<String?>(null)
    private var tts: OfflineTts? = null

    override suspend fun prepare(model: InstalledModel) = withContext(Dispatchers.Default) {
        lifecycleMutex.withLock {
            val root = File(model.path).canonicalFile
            require(root.isDirectory) { "Supertonic 모델 폴더를 찾을 수 없어요" }
            val files = REQUIRED_FILES.associateWith { name ->
                root.resolve(name).canonicalFile.also { file ->
                    require(file.parentFile == root && file.isFile) { "Supertonic 모델 파일이 빠졌어요: $name" }
                }.absolutePath
            }
            tts?.release()
            tts = OfflineTts(
                config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        supertonic = OfflineTtsSupertonicModelConfig(
                            durationPredictor = files.getValue("duration_predictor.int8.onnx"),
                            textEncoder = files.getValue("text_encoder.int8.onnx"),
                            vectorEstimator = files.getValue("vector_estimator.int8.onnx"),
                            vocoder = files.getValue("vocoder.int8.onnx"),
                            ttsJson = files.getValue("tts.json"),
                            unicodeIndexer = files.getValue("unicode_indexer.bin"),
                            voiceStyle = files.getValue("voice.bin"),
                        ),
                        numThreads = numThreads.coerceIn(1, 4),
                        debug = false,
                        provider = "cpu",
                    ),
                    maxNumSentences = 1,
                ),
            )
        }
    }

    override fun synthesize(turnId: TurnId, text: String): Flow<PcmChunk> = flow {
        require(text.isNotBlank() && text.length <= MAX_TEXT_LENGTH) { "읽을 문장이 너무 길거나 비어 있어요" }
        check(activeTurn.compareAndSet(null, turnId.value)) { "이미 음성을 만들고 있어요" }
        try {
            val audio = lifecycleMutex.withLock {
                val engine = checkNotNull(tts) { "Supertonic 모델을 먼저 설치해 주세요" }
                engine.generateWithConfig(
                    text = normalizeForSpeech(text),
                    config = GenerationConfig(
                        sid = speakerId.coerceIn(0, 9),
                        speed = speed.coerceIn(0.8f, 1.25f),
                        numSteps = 8,
                        extra = mapOf("lang" to "ko"),
                    ),
                )
            }
            if (activeTurn.get() == turnId.value) {
                val pcm = ShortArray(audio.samples.size) { index ->
                    (audio.samples[index].coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort()
                }
                pcm.asList().chunked(PCM_CHUNK_SAMPLES).forEach { samples ->
                    if (activeTurn.get() != turnId.value) return@flow
                    emit(PcmChunk(samples.toShortArray(), audio.sampleRate))
                }
            }
        } finally {
            activeTurn.compareAndSet(turnId.value, null)
        }
    }.flowOn(Dispatchers.Default)

    override suspend fun cancel(turnId: TurnId) {
        activeTurn.compareAndSet(turnId.value, null)
    }

    override suspend fun release() = lifecycleMutex.withLock {
        activeTurn.set(null)
        tts?.release()
        tts = null
    }

    private fun normalizeForSpeech(value: String): String = value
        .replace(Regex("https?://\\S+"), "링크")
        .replace(Regex("[*_`#>]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    companion object {
        const val MAX_TEXT_LENGTH = 500
        const val PCM_CHUNK_SAMPLES = 22_050
        val REQUIRED_FILES = setOf(
            "duration_predictor.int8.onnx",
            "text_encoder.int8.onnx",
            "vector_estimator.int8.onnx",
            "vocoder.int8.onnx",
            "tts.json",
            "unicode_indexer.bin",
            "voice.bin",
        )
    }
}

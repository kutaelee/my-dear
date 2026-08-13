package app.mydear.android.runtime.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import app.mydear.android.domain.SpeechToTextEngine
import app.mydear.android.domain.SttAvailability
import app.mydear.android.domain.SttEvent
import app.mydear.android.domain.TurnId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class AndroidOnDeviceSttEngine(context: Context) : SpeechToTextEngine {
    private val appContext = context.applicationContext
    private val active = ConcurrentHashMap<TurnId, SpeechRecognizer>()
    private val mainHandler = Handler(Looper.getMainLooper())

    override suspend fun availability(locale: Locale): SttAvailability = withContext(Dispatchers.Main.immediate) {
        if (SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)) SttAvailability.Ready
        else SttAvailability.Unsupported
    }

    override fun recognize(turnId: TurnId, locale: Locale): Flow<SttEvent> = callbackFlow {
        val recognizer = withContext(Dispatchers.Main.immediate) {
            check(SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)) {
                "이 휴대폰에는 온디바이스 음성 인식이 준비되지 않았어요"
            }
            SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
        }
        val previous = active.putIfAbsent(turnId, recognizer)
        check(previous == null) { "이미 음성을 듣고 있어요" }

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults.bestTranscript()?.let { trySend(SttEvent.Partial(it)) }
            }
            override fun onResults(results: Bundle?) {
                val text = results.bestTranscript()
                if (text.isNullOrBlank()) trySend(SttEvent.Failure("말씀을 알아듣지 못했어요"))
                else trySend(SttEvent.Final(text))
                close()
            }
            override fun onError(error: Int) {
                trySend(SttEvent.Failure(userMessage(error)))
                close()
            }
        }

        withContext(Dispatchers.Main.immediate) {
            recognizer.setRecognitionListener(listener)
            recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            })
        }

        awaitClose {
            active.remove(turnId, recognizer)
            mainHandler.post {
                recognizer.cancel()
                recognizer.destroy()
            }
        }
    }

    override suspend fun cancel(turnId: TurnId) = withContext(Dispatchers.Main.immediate) {
        active.remove(turnId)?.run {
            cancel()
            destroy()
        }
        Unit
    }

    private fun Bundle?.bestTranscript(): String? = this
        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?.firstOrNull()
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    private fun userMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> "말씀을 알아듣지 못했어요. 다시 말씀해 주세요."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "말씀이 들리지 않았어요. 다시 눌러 주세요."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한이 필요해요. 설정에서 켤 수 있어요."
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "한국어 음성 인식 모델을 사용할 수 없어요."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "잠시 후 다시 말씀해 주세요."
        else -> "음성 인식에 문제가 생겼어요. 다시 시도해 주세요."
    }
}

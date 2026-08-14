package app.mydear.android.runtime.stt

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.ModelDownloadListener
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import app.mydear.android.domain.SpeechToTextEngine
import app.mydear.android.domain.SttAvailability
import app.mydear.android.domain.SttEvent
import app.mydear.android.domain.SttModelRequest
import app.mydear.android.domain.TurnId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class AndroidOnDeviceSttEngine(
    context: Context,
    private val useSystemSpeechService: Boolean = false,
) : SpeechToTextEngine {
    private val appContext = context.applicationContext
    private val active = ConcurrentHashMap<TurnId, SpeechRecognizer>()
    private val mainHandler = Handler(Looper.getMainLooper())

    override suspend fun availability(locale: Locale): SttAvailability = withContext(Dispatchers.Main.immediate) {
        if (useSystemSpeechService) {
            return@withContext if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
                SttAvailability.Ready
            } else {
                SttAvailability.Unsupported
            }
        }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)) return@withContext SttAvailability.Unsupported
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@withContext SttAvailability.Ready
        checkLanguageSupport(locale)
    }

    override suspend fun requestLanguageModel(locale: Locale): SttModelRequest = withContext(Dispatchers.Main.immediate) {
        if (useSystemSpeechService) return@withContext SttModelRequest.Ready
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)) return@withContext SttModelRequest.ManualInstallRequired
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@withContext SttModelRequest.ManualInstallRequired
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) requestLanguageModelWithProgress(locale)
        else requestLanguageModelWithoutProgress(locale)
    }

    override fun recognize(turnId: TurnId, locale: Locale): Flow<SttEvent> = callbackFlow {
        val recognizer = withContext(Dispatchers.Main.immediate) {
            if (useSystemSpeechService) {
                check(SpeechRecognizer.isRecognitionAvailable(appContext)) {
                    "이 휴대폰에는 기본 음성 입력 서비스가 준비되지 않았어요"
                }
                SpeechRecognizer.createSpeechRecognizer(appContext)
            } else {
                check(SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)) {
                    "이 휴대폰에는 온디바이스 음성 인식이 준비되지 않았어요"
                }
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            }
        }
        val cleanup = RegisteredResourceCleanup(active, turnId, recognizer) { resource ->
            mainHandler.post {
                runCatching { resource.cancel() }
                runCatching { resource.destroy() }
            }
        }
        val previous = active.putIfAbsent(turnId, recognizer)
        if (previous != null) {
            cleanup.run(forceClose = true)
            error("이미 음성을 듣고 있어요")
        }

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
                trySend(recognitionErrorEvent(error))
                close()
            }
        }

        try {
            withContext(Dispatchers.Main.immediate) {
                recognizer.setRecognitionListener(listener)
                recognizer.startListening(recognitionIntent(locale))
            }
        } catch (error: Throwable) {
            cleanup.run()
            throw error
        }

        awaitClose {
            cleanup.run()
        }
    }

    override suspend fun cancel(turnId: TurnId) = withContext(Dispatchers.Main.immediate) {
        active.remove(turnId)?.run {
            cancel()
            destroy()
        }
        Unit
    }

    internal fun recognitionErrorEvent(error: Int): SttEvent =
        if (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE || error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED) {
            if (useSystemSpeechService) {
                SttEvent.Failure("휴대폰 기본 음성 입력에서 한국어를 사용할 수 없어요")
            } else {
                SttEvent.ModelDownloadRequired
            }
        } else {
            SttEvent.Failure(userMessage(error))
        }

    private fun Bundle?.bestTranscript(): String? = this
        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?.firstOrNull()
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    private fun recognitionIntent(locale: Locale) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale.toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !useSystemSpeechService)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun checkLanguageSupport(locale: Locale): SttAvailability = suspendCancellableCoroutine { continuation ->
        val recognizer = runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext) }.getOrElse {
            continuation.resume(SttAvailability.Unsupported)
            return@suspendCancellableCoroutine
        }
        val completed = AtomicBoolean(false)
        fun complete(result: SttAvailability) {
            if (!completed.compareAndSet(false, true)) return
            if (continuation.isActive) continuation.resume(result)
            recognizer.destroy()
        }
        continuation.invokeOnCancellation {
            if (completed.compareAndSet(false, true)) mainHandler.post(recognizer::destroy)
        }
        runCatching {
            recognizer.checkRecognitionSupport(
                recognitionIntent(locale),
                appContext.mainExecutor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                        complete(
                            resolveLanguageSupport(
                                recognitionSupport.installedOnDeviceLanguages,
                                recognitionSupport.pendingOnDeviceLanguages,
                                recognitionSupport.supportedOnDeviceLanguages,
                                locale,
                            ),
                        )
                    }

                    override fun onError(error: Int) {
                        complete(resolveLanguageSupportError(error))
                    }
                },
            )
        }.onFailure { complete(resolveLanguageSupportError(null)) }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestLanguageModelWithoutProgress(locale: Locale): SttModelRequest {
        val recognizer = runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext) }.getOrNull()
            ?: return SttModelRequest.ManualInstallRequired
        return try {
            recognizer.triggerModelDownload(recognitionIntent(locale))
            SttModelRequest.Scheduled
        } catch (_: Exception) {
            SttModelRequest.ManualInstallRequired
        } finally {
            recognizer.destroy()
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun requestLanguageModelWithProgress(locale: Locale): SttModelRequest = suspendCancellableCoroutine { continuation ->
        val recognizer = runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext) }.getOrElse {
            continuation.resume(SttModelRequest.ManualInstallRequired)
            return@suspendCancellableCoroutine
        }
        val completed = AtomicBoolean(false)
        fun complete(result: SttModelRequest) {
            if (!completed.compareAndSet(false, true)) return
            if (continuation.isActive) continuation.resume(result)
            recognizer.destroy()
        }
        continuation.invokeOnCancellation {
            if (completed.compareAndSet(false, true)) mainHandler.post(recognizer::destroy)
        }
        runCatching {
            recognizer.triggerModelDownload(
                recognitionIntent(locale),
                appContext.mainExecutor,
                object : ModelDownloadListener {
                    override fun onProgress(completedPercent: Int) = Unit
                    override fun onSuccess() = complete(SttModelRequest.Ready)
                    override fun onScheduled() = complete(SttModelRequest.Scheduled)
                    override fun onError(error: Int) = complete(
                        if (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED) SttModelRequest.ManualInstallRequired
                        else SttModelRequest.Failed,
                    )
                },
            )
        }.onFailure { complete(SttModelRequest.ManualInstallRequired) }
    }

    private fun userMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> "말씀을 알아듣지 못했어요. 다시 말씀해 주세요."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "말씀이 들리지 않았어요. 다시 눌러 주세요."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한이 필요해요. 설정에서 켤 수 있어요."
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "한국어 음성 인식 모델을 사용할 수 없어요."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "잠시 후 다시 말씀해 주세요."
        else -> "오프라인 음성 인식을 시작하지 못했어요."
    }
}

internal fun resolveLanguageSupport(
    installed: Collection<String>,
    pending: Collection<String>,
    supported: Collection<String>,
    locale: Locale,
): SttAvailability = when {
    installed.supportsLanguage(locale) -> SttAvailability.Ready
    pending.supportsLanguage(locale) -> SttAvailability.ModelDownloadRequired
    supported.supportsLanguage(locale) -> SttAvailability.ModelDownloadRequired
    installed.isEmpty() && pending.isEmpty() && supported.isEmpty() -> SttAvailability.ModelDownloadRequired
    else -> SttAvailability.Unsupported
}

internal fun resolveLanguageSupportError(error: Int?): SttAvailability =
    if (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED) SttAvailability.Unsupported
    else SttAvailability.ModelDownloadRequired

internal fun Collection<String>.supportsLanguage(locale: Locale): Boolean = any { tag ->
    val candidate = Locale.forLanguageTag(tag.replace('_', '-'))
    candidate.language.equals(locale.language, ignoreCase = true)
}

internal class RegisteredResourceCleanup<K, V>(
    private val registry: ConcurrentHashMap<K, V>,
    private val key: K,
    private val value: V,
    private val close: (V) -> Unit,
) {
    private val completed = AtomicBoolean(false)

    fun run(forceClose: Boolean = false) {
        if (!completed.compareAndSet(false, true)) return
        val removed = registry.remove(key, value)
        if (removed || forceClose) close(value)
    }
}

package app.mydear.android.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceRetryPolicyTest {
    @Test fun offlineLanguageModelRetriesOnlyOnceBeforeOfferingRecovery() {
        assertTrue(canRetryOfflineModel(0))
        assertFalse(canRetryOfflineModel(1))
        assertFalse(canRetryOfflineModel(2))
    }

    @Test fun recognizerStartExceptionReturnsToRecoverableVoiceState() = runBlocking {
        var failure: Pair<String, Boolean>? = null

        runSpeechRecognitionGuard(
            isOffline = true,
            onFailure = { message, fallback -> failure = message to fallback },
        ) {
            throw IllegalStateException("recognizer start failed")
        }

        assertTrue(failure?.first?.contains("오프라인 음성 인식을 시작하지 못했어요") == true)
        assertEquals(true, failure?.second)
    }

    @Test fun recognitionCancellationIsNotConvertedIntoAnError() = runBlocking {
        var failed = false
        var cancellationPropagated = false

        try {
            runSpeechRecognitionGuard(
                isOffline = true,
                onFailure = { _, _ -> failed = true },
            ) {
                throw CancellationException("user stopped listening")
            }
        } catch (_: CancellationException) {
            cancellationPropagated = true
        }

        assertTrue(cancellationPropagated)
        assertFalse(failed)
    }
}

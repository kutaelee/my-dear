package app.mydear.android.runtime.stt

import android.speech.SpeechRecognizer
import app.mydear.android.domain.SttAvailability
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class AndroidOnDeviceSttSupportTest {
    @Test fun KoreanRegionalAndLanguageOnlyTagsMatchKoKr() {
        assertTrue(listOf("ko-KR").supportsLanguage(Locale.KOREA))
        assertTrue(listOf("ko").supportsLanguage(Locale.KOREA))
        assertTrue(listOf("ko_KR").supportsLanguage(Locale.KOREA))
        assertFalse(listOf("en-US", "ja-JP").supportsLanguage(Locale.KOREA))
    }

    @Test fun InstalledKoreanIsReadyAndDownloadableKoreanRequestsModel() {
        assertEquals(
            SttAvailability.Ready,
            resolveLanguageSupport(listOf("ko-KR"), emptyList(), emptyList(), Locale.KOREA),
        )
        assertEquals(
            SttAvailability.ModelDownloadRequired,
            resolveLanguageSupport(emptyList(), emptyList(), listOf("ko-KR"), Locale.KOREA),
        )
        assertEquals(
            SttAvailability.ModelDownloadRequired,
            resolveLanguageSupport(emptyList(), listOf("ko"), emptyList(), Locale.KOREA),
        )
        assertEquals(
            SttAvailability.Unsupported,
            resolveLanguageSupport(listOf("en-US"), emptyList(), emptyList(), Locale.KOREA),
        )
        assertEquals(
            SttAvailability.ModelDownloadRequired,
            resolveLanguageSupport(emptyList(), emptyList(), emptyList(), Locale.KOREA),
        )
    }

    @Test fun supportCheckErrorsNeverPretendTheKoreanModelIsReady() {
        assertEquals(
            SttAvailability.Unsupported,
            resolveLanguageSupportError(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED),
        )
        assertEquals(
            SttAvailability.ModelDownloadRequired,
            resolveLanguageSupportError(SpeechRecognizer.ERROR_CLIENT),
        )
        assertEquals(
            SttAvailability.ModelDownloadRequired,
            resolveLanguageSupportError(null),
        )
    }

    @Test fun recognizerSetupFailureRemovesAndClosesTheRegisteredResourceExactlyOnce() {
        val registry = ConcurrentHashMap<String, String>()
        registry["turn"] = "recognizer"
        var closeCount = 0
        val cleanup = RegisteredResourceCleanup(registry, "turn", "recognizer") { closeCount++ }

        cleanup.run()
        cleanup.run()

        assertTrue(registry.isEmpty())
        assertEquals(1, closeCount)
    }

    @Test fun supportCallbackThatNeverReturnsFallsBackWithinTheBound() = runBlocking {
        val result = boundedSpeechSupportCheck(
            timeoutMs = 25,
            fallback = SttAvailability.ModelDownloadRequired,
        ) {
            awaitCancellation()
        }

        assertEquals(SttAvailability.ModelDownloadRequired, result)
    }
}

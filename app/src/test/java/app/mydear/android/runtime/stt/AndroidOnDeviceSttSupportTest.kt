package app.mydear.android.runtime.stt

import app.mydear.android.domain.SttAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

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
            SttAvailability.Ready,
            resolveLanguageSupport(emptyList(), emptyList(), emptyList(), Locale.KOREA),
        )
    }
}

package app.mydear.android.runtime.stt

import android.speech.SpeechRecognizer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.mydear.android.domain.SttEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class AndroidOnDeviceSttAvdTest {
    @Test fun KoreanLanguageSupportCheckReturnsWithoutCrashing() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val availability = AndroidOnDeviceSttEngine(context).availability(Locale.KOREA)
        assertNotNull(availability)
    }

    @Test fun systemSpeechFallbackIsAvailableOnTheAvd() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val availability = AndroidOnDeviceSttEngine(context, useSystemSpeechService = true).availability(Locale.KOREA)
        assertEquals(app.mydear.android.domain.SttAvailability.Ready, availability)
    }

    @Test fun systemSpeechKoreanUnsupportedBecomesAClosableFailure() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = AndroidOnDeviceSttEngine(context, useSystemSpeechService = true)
        val event = engine.recognitionErrorEvent(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED)

        assertTrue(event is SttEvent.Failure)
    }
}

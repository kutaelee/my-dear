package app.mydear.android.runtime.stt

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
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
}

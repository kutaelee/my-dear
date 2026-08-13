package app.mydear.android.runtime.search

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mydear.android.domain.SearchRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PublicInternetSearchGatewayTest {
    private val gateway = PublicInternetSearchGateway()

    @Test fun retrievesCurrentKoreanKnowledge() = runBlocking {
        val evidence = gateway.search(SearchRequest("대통령이 누구야")).getOrThrow()
        assertTrue(evidence.documents.isNotEmpty())
        assertTrue(evidence.documents.any { it.host == "ko.wikipedia.org" && it.snippet.length > 30 })
        assertTrue(evidence.documents.any { it.snippet.contains("이재명") })
    }

    @Test fun retrievesWeatherForKoreanTownName() = runBlocking {
        val evidence = gateway.search(SearchRequest("오늘 와부읍 날씨 알려줘")).getOrThrow()
        val weather = evidence.documents.single()
        assertTrue(weather.host == "open-meteo.com")
        assertTrue(weather.snippet.contains("와부읍"))
        assertTrue(weather.snippet.contains("현재"))
    }

    @Test fun rejectsUnsupportedVolatileCategoryAtGatewayBoundary() = runBlocking {
        assertTrue(gateway.search(SearchRequest("오늘 환율 알려줘")).isFailure)
    }
}

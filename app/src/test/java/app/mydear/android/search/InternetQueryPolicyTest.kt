package app.mydear.android.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InternetQueryPolicyTest {
    @Test fun routesOnlyQuestionsThatBenefitFromCurrentInformation() {
        assertTrue(InternetQueryPolicy.shouldUseInternet("오늘 와부읍 날씨 알려줘", broadSearchAvailable = false))
        assertTrue(InternetQueryPolicy.shouldUseInternet("대통령이 누구야", broadSearchAvailable = false))
        assertFalse(InternetQueryPolicy.shouldUseInternet("오늘 환율", broadSearchAvailable = false))
        assertTrue(InternetQueryPolicy.shouldUseInternet("오늘 환율", broadSearchAvailable = true))
        assertFalse(InternetQueryPolicy.shouldUseInternet("담요 개는법", broadSearchAvailable = false))
        assertFalse(InternetQueryPolicy.shouldUseInternet("아무 담요나", broadSearchAvailable = false))
        assertFalse(InternetQueryPolicy.shouldUseInternet("오늘 저녁 뭐 먹지", broadSearchAvailable = false))
    }

    @Test fun extractsKoreanWeatherLocationFromNaturalQuestion() {
        assertEquals("와부읍", InternetQueryPolicy.weatherLocation("오늘 와부읍 날씨알려줘"))
        assertEquals("부산 해운대", InternetQueryPolicy.weatherLocation("내일 부산 해운대 날씨 어때요?"))
        assertEquals("은평구", InternetQueryPolicy.weatherLocation("은평구 날씨 알려줘"))
        assertEquals(null, InternetQueryPolicy.weatherLocation("오늘 날씨 알려줘"))
    }

    @Test fun rewritesImplicitKoreanPresidentQuestionForRelevantEvidence() {
        assertEquals("대한민국 대통령 목록", InternetQueryPolicy.knowledgeQuery("대통령이 누구야"))
        assertEquals("미국 대통령이 누구야", InternetQueryPolicy.knowledgeQuery("미국 대통령이 누구야"))
    }

    @Test fun detectsUnsupportedCurrentFactsWithoutMisroutingCasualTodayQuestion() {
        assertTrue(InternetQueryPolicy.requiresFreshness("서울시장이 누구야"))
        assertTrue(InternetQueryPolicy.isUnsupportedByPublicFallback("올해 최저임금 얼마야"))
        assertFalse(InternetQueryPolicy.requiresFreshness("오늘 저녁 뭐 먹지"))
        assertFalse(InternetQueryPolicy.requiresFreshness("지금 담요 개는 법"))
        assertFalse(InternetQueryPolicy.requiresFreshness("현재 설정을 알려줘"))
        assertTrue(InternetQueryPolicy.shouldUseInternet("서울시장이 누구야", broadSearchAvailable = true))
        assertFalse(InternetQueryPolicy.requiresFreshness("시장에 장 보러 갈 때 준비물"))
        assertFalse(InternetQueryPolicy.requiresFreshness("오늘 일정 정리해줘"))
        assertFalse(InternetQueryPolicy.requiresFreshness("가격표 만드는 법"))
        assertTrue(InternetQueryPolicy.requiresFreshness("오늘 휘발유 가격 얼마야"))
        assertTrue(InternetQueryPolicy.requiresFreshness("오늘 야구 경기 일정 알려줘"))
        assertFalse(InternetQueryPolicy.shouldUseInternet("내 일정 확인해줘", broadSearchAvailable = true))
        assertFalse(InternetQueryPolicy.shouldUseInternet("엄마 전화번호 찾아줘", broadSearchAvailable = true))
        assertFalse(InternetQueryPolicy.requiresFreshness("내일 병원 일정 알려줘"))
        assertTrue(InternetQueryPolicy.shouldUseInternet("인터넷에서 남양주 행사 검색해줘", broadSearchAvailable = true))
    }
}

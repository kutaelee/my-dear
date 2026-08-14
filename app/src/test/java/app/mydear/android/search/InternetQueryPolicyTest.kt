package app.mydear.android.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InternetQueryPolicyTest {
    @Test fun routesOnlyQuestionsThatBenefitFromCurrentInformation() {
        assertTrue(InternetQueryPolicy.shouldUseInternet("오늘 와부읍 날씨 알려줘", broadSearchAvailable = false))
        assertTrue(InternetQueryPolicy.shouldUseInternet("대통령이 누구야", broadSearchAvailable = false))
        assertTrue(InternetQueryPolicy.shouldUseInternet("리센느 리더는?", broadSearchAvailable = false))
        assertTrue(InternetQueryPolicy.shouldUseInternet("미스트롯의 우승자는?", broadSearchAvailable = false))
        assertFalse(InternetQueryPolicy.shouldUseInternet("누구라고", broadSearchAvailable = false))
        assertFalse(InternetQueryPolicy.shouldUseInternet("오늘 환율", broadSearchAvailable = false))
        assertTrue(InternetQueryPolicy.shouldUseInternet("오늘 환율", broadSearchAvailable = true))
        assertFalse(InternetQueryPolicy.shouldUseInternet("담요 개는법", broadSearchAvailable = false))
        assertFalse(InternetQueryPolicy.shouldUseInternet("담요 개는 법 알려줘", broadSearchAvailable = false))
        assertFalse(InternetQueryPolicy.shouldUseInternet("아무 담요나", broadSearchAvailable = false))
        assertFalse(InternetQueryPolicy.shouldUseInternet("오늘 저녁 뭐 먹지", broadSearchAvailable = false))
        assertFalse(InternetQueryPolicy.shouldUseInternet("오늘 저녁 뭐 먹을지 알려줘", broadSearchAvailable = false))
        assertFalse(InternetQueryPolicy.shouldUseInternet("엄마는 누구야", broadSearchAvailable = false))
        assertFalse(InternetQueryPolicy.shouldUseInternet("제 주민번호가 뭐야", broadSearchAvailable = false))
        assertFalse(InternetQueryPolicy.shouldUseInternet("우리 집 주소가 어디야", broadSearchAvailable = false))
        assertFalse(InternetQueryPolicy.shouldUseInternet("우리 회사 대표가 누구야?", broadSearchAvailable = false))
        assertFalse(InternetQueryPolicy.shouldUseInternet("우리 동아리 리더는 누구야?", broadSearchAvailable = false))
        assertFalse(InternetQueryPolicy.shouldUseInternet("우리회사 대표가 누구야?", broadSearchAvailable = false))
        assertFalse(InternetQueryPolicy.shouldUseInternet("우리동아리 리더는?", broadSearchAvailable = false))
        assertFalse(InternetQueryPolicy.shouldUseInternet("저희팀 리더는?", broadSearchAvailable = false))
        assertFalse(InternetQueryPolicy.shouldUseInternet("내회사 대표는?", broadSearchAvailable = false))
        assertFalse(InternetQueryPolicy.shouldUseInternet("내가 배우가 될 수 있을까?", broadSearchAvailable = false))
        assertFalse(InternetQueryPolicy.shouldUseInternet("내 남편이 배우야?", broadSearchAvailable = false))
        assertFalse(InternetQueryPolicy.shouldUseInternet("제 딸이 가수야?", broadSearchAvailable = false))
        assertFalse(InternetQueryPolicy.shouldUseInternet("내가 대통령이 될 수 있을까?", broadSearchAvailable = false))
        assertFalse(InternetQueryPolicy.shouldUseInternet("내가 대통령이 될 수 있을까?", broadSearchAvailable = true))
        assertFalse(InternetQueryPolicy.needsExternalKnowledge("내가 대통령이 될 수 있을까?"))
        assertFalse(InternetQueryPolicy.shouldUseInternet("회사 대표가 누구야?", broadSearchAvailable = false))
        assertTrue(InternetQueryPolicy.shouldUseInternet("삼성전자 대표가 누구야?", broadSearchAvailable = false))
        assertTrue(InternetQueryPolicy.shouldUseInternet("오늘 와부읍 날씨 정리해줘", broadSearchAvailable = false))
        assertTrue(InternetQueryPolicy.shouldUseInternet("삼성전자 대표를 검색해서 정리해줘", broadSearchAvailable = false))
        assertFalse(InternetQueryPolicy.shouldUseInternet("인터넷에서 최신 뉴스를 검색해서 정리해줘", broadSearchAvailable = false))
        assertTrue(InternetQueryPolicy.shouldUseInternet("인터넷에서 최신 뉴스를 검색해서 정리해줘", broadSearchAvailable = true))
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
        assertTrue(InternetQueryPolicy.isUnsupportedByPublicFallback("인터넷에서 남양주 행사 검색해줘"))
        assertTrue(InternetQueryPolicy.isEllipticalFollowUp("누구라고"))
        assertFalse(InternetQueryPolicy.isEllipticalFollowUp("리센느 리더는?"))
        assertEquals(
            InternetQueryPolicy.DetailedKnowledgeKind.Winner,
            InternetQueryPolicy.detailedKnowledgeKind("미스트롯의 우승자는?"),
        )
    }
}

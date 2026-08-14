package app.mydear.android.search

import app.mydear.android.domain.SearchDocument
import app.mydear.android.domain.SearchEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchAnswerPolicyTest {
    private val evidence = SearchEvidence(
        listOf(
            SearchDocument(
                "대한민국 대통령 목록",
                "ko.wikipedia.org",
                "https://ko.wikipedia.org/wiki/test",
                "현 대한민국 대통령은 제21대 대통령 선거에서 승리하여 2025년 6월 4일 취임한 이재명이다.",
                null,
            ),
            SearchDocument("설명", "example.org", "https://example.org", "다른 자료", null),
        ),
    )

    @Test fun currentPresidentAnswerIsDeterministicWithoutInlineCitationToken() {
        val answer = SearchAnswerPolicy.publicAnswer("우리나라 대통령 이름", evidence).orEmpty()
        assertEquals("현재 대한민국 대통령은 이재명입니다.", answer)
        assertEquals(listOf(0), SearchAnswerPolicy.publicSourceIndices("우리나라 대통령 이름", evidence))
    }

    @Test fun missingModelCitationStillKeepsVisibleSources() {
        assertEquals(listOf(0), SearchAnswerPolicy.sourceIndices("검색 내용을 정리한 답변", 2))
        assertEquals(listOf(1), SearchAnswerPolicy.sourceIndices("답변 [자료 2] [자료 99]", 2))
        assertEquals(listOf(1), SearchAnswerPolicy.sourceIndices("답변 [자료2]", 2))
        assertTrue(SearchAnswerPolicy.sourceIndices("답변", 0).isEmpty())
    }

    @Test fun publicRoleAnswerIsDeterministicAndUsesTheMatchingPage() {
        val roleEvidence = SearchEvidence(
            listOf(
                SearchDocument(
                    "리센느",
                    "ko.wikipedia.org",
                    "https://ko.wikipedia.org/wiki/%EB%A6%AC%EC%84%BC%EB%8A%90",
                    "확인된 구성 정보: 리더는 원이입니다. 리센느는 대한민국의 5인조 걸 그룹입니다.",
                    null,
                ),
            ),
        )

        assertEquals("리센느의 리더는 원이입니다.", SearchAnswerPolicy.publicAnswer("리센느 리더는?", roleEvidence))
        assertEquals(listOf(0), SearchAnswerPolicy.publicSourceIndices("리센느 리더는?", roleEvidence))
    }

    @Test fun publicWinnerAnswerIsDeterministicAndUsesTheMatchingPage() {
        val winnerEvidence = SearchEvidence(
            listOf(
                SearchDocument(
                    "내일은 미스트롯",
                    "ko.wikipedia.org",
                    "https://ko.wikipedia.org/wiki/test",
                    "확인된 공개 정보: 우승자는 송가인입니다. 대한민국의 트롯 오디션 프로그램입니다.",
                    null,
                ),
            ),
        )

        assertEquals(
            "내일은 미스트롯의 우승자는 송가인입니다.",
            SearchAnswerPolicy.publicAnswer("미스트롯의 우승자는?", winnerEvidence),
        )
        assertEquals(listOf(0), SearchAnswerPolicy.publicSourceIndices("미스트롯의 우승자는?", winnerEvidence))
    }
}

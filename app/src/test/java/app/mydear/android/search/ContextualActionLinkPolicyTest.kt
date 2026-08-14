package app.mydear.android.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextualActionLinkPolicyTest {
    @Test fun shortProductLinkFollowUpUsesThePreviousPublicProductTopic() {
        val link = ContextualActionLinkPolicy.create(
            currentQuery = "제품명이랑 링크줘",
            previousUserMessages = listOf("가성비 좋은 북쉘프 스피커 추천해줘"),
        )

        requireNotNull(link)
        assertEquals("가성비 좋은 북쉘프 스피커 찾아보기", link.title)
        assertTrue(link.url.startsWith("https://search.naver.com/search.naver?query="))
        assertTrue(link.url.contains("%EB%B6%81%EC%89%98%ED%94%84"))
    }

    @Test fun privateContextAndNonProductLinkRequestsFailClosed() {
        assertNull(
            ContextualActionLinkPolicy.create(
                "제품명이랑 링크줘",
                listOf("우리 집 주소 근처 스피커 가게 찾아줘"),
            ),
        )
        assertNull(ContextualActionLinkPolicy.create("그 사람 링크줘", listOf("우리 가족 이야기")))
    }
}

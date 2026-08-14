package app.mydear.android.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertNull(ContextualActionLinkPolicy.create("가성비 제품으로 링크줘", emptyList()))
    }

    @Test fun genericValueFollowUpKeepsThePreviousProductCategory() {
        val link = ContextualActionLinkPolicy.create(
            currentQuery = "가성비 제품으로 링크줘",
            previousUserMessages = listOf("요즘 잘나가는 방향제 추천해줘"),
        )

        requireNotNull(link)
        assertEquals("가성비 방향제 찾아보기", link.title)
        assertTrue(link.url.contains("%EA%B0%80%EC%84%B1%EB%B9%84"))
        assertTrue(link.url.contains("%EB%B0%A9%ED%96%A5%EC%A0%9C"))
    }

    @Test fun previousFreeTextIsReducedToSafeProductTermsBeforeOpeningTheBrowser() {
        val link = ContextualActionLinkPolicy.create(
            currentQuery = "제품명이랑 링크줘",
            previousUserMessages = listOf("김민수에게 가성비 좋은 스피커 추천해줘"),
        )

        requireNotNull(link)
        assertEquals("가성비 좋은 스피커 찾아보기", link.title)
        assertFalse(link.title.contains("김민수"))
        assertFalse(link.url.contains("%EA%B9%80%EB%AF%BC%EC%88%98"))
    }

    @Test fun explicitNewTvRequestReplacesThePreviousAirFreshenerTopic() {
        val link = ContextualActionLinkPolicy.create(
            currentQuery = "티비 고장났는데 새로 사게 링크줘",
            previousUserMessages = listOf("요즘 잘나가는 방향제 추천해줘", "가성비 제품으로 링크줘"),
        )

        requireNotNull(link)
        assertEquals("TV 찾아보기", link.title)
        assertTrue(link.url.contains("query=TV"))
        assertFalse(link.title.contains("방향제"))
        assertFalse(link.url.contains("%EB%B0%A9%ED%96%A5%EC%A0%9C"))
        assertFalse(link.url.contains("%EA%B3%A0%EC%9E%A5"))
    }
}

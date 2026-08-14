package app.mydear.android.storage

import androidx.test.platform.app.InstrumentationRegistry
import app.mydear.android.domain.ChatMessage
import app.mydear.android.domain.Provenance
import app.mydear.android.domain.Role
import app.mydear.android.domain.SearchSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedChatStoreTest {
    @Test fun KoreanConversationIsEncryptedAtRestAndRoundTrips() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = "민감한 한국어 대화 8291"
        val store = EncryptedChatStore(context)
        val source = SearchSource("대한민국 대통령 목록", "ko.wikipedia.org", "https://ko.wikipedia.org/wiki/test", null)
        store.save(
            listOf(
                ChatMessage("secure-test", Role.User, fixture),
                ChatMessage("web-test", Role.Assistant, "검색 답변", Provenance.Web(1234L, listOf(source))),
                ChatMessage(
                    "action-test",
                    Role.Assistant,
                    "제품을 골라드렸어요",
                    Provenance.ActionLink(
                        "북쉘프 스피커 찾아보기",
                        "https://search.naver.com/search.naver?query=%EB%B6%81%EC%89%98%ED%94%84",
                    ),
                ),
            ),
        )
        val rawFiles = context.filesDir.resolve("private-chat").walkTopDown().filter { it.isFile }.toList()
        assertFalse(rawFiles.any { it.readBytes().decodeToString().contains(fixture) })
        val loaded = store.load()
        assertEquals(fixture, loaded.first().text)
        val web = loaded[1].provenance as Provenance.Web
        assertEquals(1234L, web.searchedAtEpochMs)
        assertEquals(listOf(source), web.sources)
        val action = loaded.last().provenance as Provenance.ActionLink
        assertEquals("북쉘프 스피커 찾아보기", action.title)
        assertTrue(action.url.startsWith("https://search.naver.com/search.naver?query="))
    }
}

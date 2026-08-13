package app.mydear.android.storage

import androidx.test.platform.app.InstrumentationRegistry
import app.mydear.android.domain.ChatMessage
import app.mydear.android.domain.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EncryptedChatStoreTest {
    @Test fun KoreanConversationIsEncryptedAtRestAndRoundTrips() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = "민감한 한국어 대화 8291"
        val store = EncryptedChatStore(context)
        store.save(listOf(ChatMessage("secure-test", Role.User, fixture)))
        val rawFiles = context.filesDir.resolve("private-chat").walkTopDown().filter { it.isFile }.toList()
        assertFalse(rawFiles.any { it.readBytes().decodeToString().contains(fixture) })
        assertEquals(fixture, store.load().last().text)
    }
}

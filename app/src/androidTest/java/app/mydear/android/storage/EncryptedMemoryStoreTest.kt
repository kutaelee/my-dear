package app.mydear.android.storage

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedMemoryStoreTest {
    @Test fun personalMemoryIsEncryptedAtRestAndCanBeForgotten() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = EncryptedMemoryStore(context)
        store.clear()
        val fixture = "내 이름은 민수 7319"
        store.remember(fixture)
        store.remember("딸 이름은 수진 4826")
        val files = context.filesDir.resolve("private-memory").walkTopDown().filter { it.isFile }.toList()
        assertFalse(files.any { it.readBytes().decodeToString().contains(fixture) })
        assertTrue(store.load().any { it.text == fixture })
        assertEquals(1, store.forgetMatching("내 이름").size)
        assertEquals(listOf("딸 이름은 수진 4826"), store.load().map { it.text })
    }
}

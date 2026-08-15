package app.mydear.android.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalMemoryTest {
    @Test fun explicitRememberAndForgetCommandsAreParsed() {
        assertEquals(MemoryCommand.Remember("내 이름은 민수야"), MemoryIntentParser.parse("내 이름은 민수야 기억해줘"))
        assertEquals(MemoryCommand.Remember("내 이름은 민수야"), MemoryIntentParser.parse("내 이름은 민수야 기억해 주세요"))
        assertEquals(MemoryCommand.Forget("내 이름"), MemoryIntentParser.parse("내 이름 잊어줘"))
        assertEquals(MemoryCommand.Forget("내 이름"), MemoryIntentParser.parse("내 이름 잊어 주세요"))
        assertEquals(MemoryCommand.ForgetAll, MemoryIntentParser.parse("기억한 정보 모두 잊어줘"))
        assertEquals(MemoryCommand.ForgetAll, MemoryIntentParser.parse("기억 모두 지워 주세요"))
        assertEquals(MemoryCommand.Recall, MemoryIntentParser.parse("나에 대해 뭘 기억하고 있어?"))
        assertNull(MemoryIntentParser.parse("‘기억 모두 지워’가 무슨 뜻이야?"))
        assertNull(MemoryIntentParser.parse("기억해줘가 무슨 뜻이야?"))
    }

    @Test fun relevantPersonalMemoryIsRetrievedLocally() {
        val memories = listOf(
            PersonalMemory("1", "내 이름은 민수야", 1),
            PersonalMemory("2", "나는 등산을 좋아해", 2),
            PersonalMemory("3", "딸 이름은 수진이야", 3),
        )
        val result = LocalMemoryRetriever.retrieve(memories, "내 이름이 뭐였지?")
        assertEquals("내 이름은 민수야", result.first().text)
        assertEquals(1, result.size)
        assertTrue(result.none { it.text.contains("등산") })
    }
}
